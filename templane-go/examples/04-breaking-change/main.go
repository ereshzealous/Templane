// 04 — Breaking-change detection between schema versions.
package main

import (
	"fmt"
	"os"

	"github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
	oldSchema := loadSchema("v1.schema.yaml")
	newSchema := loadSchema("v2.schema.yaml")

	changes := core.DetectBreakingChanges(oldSchema, newSchema)
	if len(changes) == 0 {
		fmt.Println("no breaking changes")
		return
	}

	fmt.Printf("%d breaking change(s) detected:\n\n", len(changes))
	for _, c := range changes {
		fmt.Printf("  [%s] %s\n      old: %s\n      new: %s\n",
			c.Category, c.FieldPath, c.Old, c.New)
	}
}

func loadSchema(name string) *core.TypedSchema {
	source, err := os.ReadFile(name)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	parsed := core.ParseSchema(string(source), name)
	if parsed.Error != "" {
		fmt.Fprintln(os.Stderr, "schema parse error:", parsed.Error)
		os.Exit(1)
	}
	return parsed.Schema
}
