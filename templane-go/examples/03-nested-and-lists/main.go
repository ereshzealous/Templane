// 03 — Nested objects, enums, lists: an order receipt validated before render.
package main

import (
	"fmt"
	"os"
	"text/template"

	"github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
	source, err := os.ReadFile("order.templane")
	must(err)

	parsed := core.ParseSchema(string(source), "order.templane")
	if parsed.Error != "" {
		fmt.Fprintln(os.Stderr, "schema parse error:", parsed.Error)
		os.Exit(1)
	}

	data := map[string]any{
		"customer": map[string]any{
			"name": "Jordan Shah",
			"tier": "pro",
		},
		"items": []any{
			map[string]any{"sku": "BOOK-042", "qty": float64(2)},
			map[string]any{"sku": "PEN-003", "qty": float64(5)},
			map[string]any{"sku": "MUG-099", "qty": float64(1)},
		},
		"total_cents": float64(5993),
	}

	if errs := core.Check(parsed.Schema, data); len(errs) > 0 {
		for _, e := range errs {
			fmt.Fprintf(os.Stderr, "  [%s] %s: %s\n", e.Code, e.Field, e.Message)
		}
		os.Exit(1)
	}

	funcs := template.FuncMap{
		"divide": func(a, b float64) float64 { return a / b },
	}
	tmpl := template.Must(template.New("order").Funcs(funcs).Parse(*parsed.Body))
	must(tmpl.Execute(os.Stdout, data))
}

func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
