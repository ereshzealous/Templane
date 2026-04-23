package core

// BreakingChange describes a schema evolution that breaks existing callers.
type BreakingChange struct {
	Category  string // "removed_field" | "required_change" | "type_change" | "enum_value_removed"
	FieldPath string
	Old       string
	New       string
}

// DetectBreakingChanges compares two schemas and returns all breaking changes.
// Non-breaking: adding optional fields, adding enum values, making required optional.
func DetectBreakingChanges(oldSchema, newSchema *TypedSchema) []BreakingChange {
	var out []BreakingChange
	detectFields(oldSchema.Fields, newSchema.Fields, "", &out)
	return out
}

func detectFields(oldFields, newFields map[string]TemplaneField, prefix string, out *[]BreakingChange) {
	for name, oldField := range oldFields {
		path := name
		if prefix != "" {
			path = prefix + "." + name
		}

		newField, present := newFields[name]
		if !present {
			*out = append(*out, BreakingChange{
				Category:  "removed_field",
				FieldPath: path,
				Old:       oldField.Type.Kind,
				New:       "<absent>",
			})
			continue
		}

		if !oldField.Required && newField.Required {
			*out = append(*out, BreakingChange{
				Category:  "required_change",
				FieldPath: path,
				Old:       "optional",
				New:       "required",
			})
		}

		if oldField.Type.Kind != newField.Type.Kind {
			*out = append(*out, BreakingChange{
				Category:  "type_change",
				FieldPath: path,
				Old:       oldField.Type.Kind,
				New:       newField.Type.Kind,
			})
			continue
		}

		if oldField.Type.Kind == "enum" {
			newValues := make(map[string]bool, len(newField.Type.Values))
			for _, v := range newField.Type.Values {
				newValues[v] = true
			}
			for _, v := range oldField.Type.Values {
				if !newValues[v] {
					*out = append(*out, BreakingChange{
						Category:  "enum_value_removed",
						FieldPath: path,
						Old:       v,
						New:       "<removed>",
					})
				}
			}
		}

		if oldField.Type.Kind == "object" {
			detectFields(oldField.Type.Fields, newField.Type.Fields, path, out)
		}
	}
}
