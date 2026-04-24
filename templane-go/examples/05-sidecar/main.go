// 05 — Sidecar mode: keep your .tmpl files, add a schema beside them.
//
// service.tmpl is plain Go text/template — editable in any Go-aware editor.
// service.schema.yaml sits next to it, declaring the data contract
// and pointing back to the .tmpl via `body: ./service.tmpl`.
package main

import (
	"fmt"
	"os"
	"text/template"

	"github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
	r := core.LoadSchemaFromPath("service.schema.yaml")
	if r.Error != "" {
		fmt.Fprintln(os.Stderr, "schema load error:", r.Error)
		os.Exit(1)
	}

	data := map[string]any{
		"description":    "Templane doc site",
		"run_as":         "templane",
		"working_dir":    "/var/lib/templane",
		"exec_start":     "/usr/local/bin/templane-serve --port 8080",
		"restart_policy": "on-failure",
		"env": []any{
			map[string]any{"name": "LOG_LEVEL", "value": "info"},
			map[string]any{"name": "HOME", "value": "/var/lib/templane"},
		},
	}

	if errs := core.Check(r.Schema, data); len(errs) > 0 {
		fmt.Fprintf(os.Stderr, "validation refused: %d error(s)\n", len(errs))
		for _, e := range errs {
			fmt.Fprintf(os.Stderr, "  [%s] %s: %s\n", e.Code, e.Field, e.Message)
		}
		os.Exit(1)
	}

	fmt.Println("--- Good data: renders cleanly ---")
	tmpl := template.Must(template.New("svc").Parse(*r.Body))
	if err := tmpl.Execute(os.Stdout, data); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	fmt.Println()
	fmt.Println("--- Bad data: type-check refuses (check only, no execute) ---")
	badData := map[string]any{
		"description":    "bad",
		"run_as":         "templane",
		"working_dir":    "/var/lib/templane",
		// exec_start missing            → missing_required_field
		"restart_policy": "sometimes",   // invalid_enum_value
		"env": []any{
			map[string]any{"name": "X"}, // value missing
		},
	}
	errs := core.Check(r.Schema, badData)
	fmt.Printf("check refused: %d error(s)\n\n", len(errs))
	for _, e := range errs {
		fmt.Printf("  [%s] %s: %s\n", e.Code, e.Field, e.Message)
	}
}
