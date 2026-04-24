// 02 — Validation errors: surface every error code in one check pass.
package main

import (
	"fmt"
	"os"

	"github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
	source, err := os.ReadFile("profile.templane")
	must(err)

	parsed := core.ParseSchema(string(source), "profile.templane")
	if parsed.Error != "" {
		fmt.Fprintln(os.Stderr, "schema parse error:", parsed.Error)
		os.Exit(1)
	}

	// Intentionally bad data to trip every error code.
	badData := map[string]any{
		// "name" missing        → missing_required_field
		"age":  "thirty",         // type_mismatch
		"role": "superuser",      // invalid_enum_value
		"rol":  "admin",          // unknown_field → did_you_mean "role"
	}

	errs := core.Check(parsed.Schema, badData)
	if len(errs) == 0 {
		fmt.Println("unexpectedly clean")
		return
	}

	fmt.Printf("check refused: %d error(s)\n\n", len(errs))
	for _, e := range errs {
		fmt.Printf("  [%s] %s: %s\n", e.Code, e.Field, e.Message)
	}
}

func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
