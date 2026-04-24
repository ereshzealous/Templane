// 01 — Hello: parse a Templane schema, type-check data, render with text/template.
//
// templane-go ships the schema parser + type checker. For rendering, this
// example uses the Go stdlib text/template against the validated data.
package main

import (
	"fmt"
	"os"
	"text/template"

	"github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
	source, err := os.ReadFile("greeting.templane")
	must(err)

	parsed := core.ParseSchema(string(source), "greeting.templane")
	if parsed.Error != "" {
		fmt.Fprintln(os.Stderr, "schema parse error:", parsed.Error)
		os.Exit(1)
	}

	data := map[string]any{
		"name":          "Arya",
		"temperature_c": float64(22),
		"is_morning":    true,
	}

	if errs := core.Check(parsed.Schema, data); len(errs) > 0 {
		for _, e := range errs {
			fmt.Fprintf(os.Stderr, "  [%s] %s: %s\n", e.Code, e.Field, e.Message)
		}
		os.Exit(1)
	}

	tmpl := template.Must(template.New("greeting").Parse(*parsed.Body))
	must(tmpl.Execute(os.Stdout, data))
}

func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
