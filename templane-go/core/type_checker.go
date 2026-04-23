package core

import (
	"fmt"
	"strings"
)

// Check validates data against a schema, returning all errors (not short-circuiting).
func Check(schema *TypedSchema, data map[string]any) []TypeCheckError {
	return checkWithPrefix(schema, data, "")
}

func checkWithPrefix(schema *TypedSchema, data map[string]any, prefix string) []TypeCheckError {
	var errors []TypeCheckError

	// Required / type checks
	for name, field := range schema.Fields {
		path := name
		if prefix != "" {
			path = prefix + "." + name
		}
		value, present := data[name]
		if !present {
			if field.Required {
				errors = append(errors, TypeCheckError{
					Code:    "missing_required_field",
					Field:   path,
					Message: fmt.Sprintf("Required field '%s' is missing", path),
				})
			}
			continue
		}
		errors = append(errors, checkType(field.Type, value, path)...)
	}

	// Unknown field / did-you-mean checks
	for key := range data {
		if _, known := schema.Fields[key]; known {
			continue
		}
		path := key
		if prefix != "" {
			path = prefix + "." + key
		}
		closest, minDist := "", -1
		for k := range schema.Fields {
			d := levenshtein(key, k)
			if minDist == -1 || d < minDist {
				minDist = d
				closest = k
			}
		}
		if closest != "" && minDist <= 3 {
			errors = append(errors, TypeCheckError{
				Code:    "did_you_mean",
				Field:   path,
				Message: fmt.Sprintf("Unknown field '%s'. Did you mean '%s'?", path, closest),
			})
		} else {
			errors = append(errors, TypeCheckError{
				Code:    "unknown_field",
				Field:   path,
				Message: fmt.Sprintf("Field '%s' is not defined in schema", path),
			})
		}
	}

	return errors
}

func checkType(t TemplaneFieldType, value any, path string) []TypeCheckError {
	var errors []TypeCheckError
	switch t.Kind {
	case "string":
		if _, ok := value.(string); !ok {
			errors = append(errors, mismatch(path, "string", value))
		}
	case "number":
		if !isNumber(value) {
			errors = append(errors, mismatch(path, "number", value))
		}
	case "boolean":
		if _, ok := value.(bool); !ok {
			errors = append(errors, mismatch(path, "boolean", value))
		}
	case "null":
		if value != nil {
			errors = append(errors, mismatch(path, "null", value))
		}
	case "enum":
		s, ok := value.(string)
		found := false
		if ok {
			for _, v := range t.Values {
				if v == s {
					found = true
					break
				}
			}
		}
		if !found {
			errors = append(errors, TypeCheckError{
				Code:    "invalid_enum_value",
				Field:   path,
				Message: fmt.Sprintf("Field '%s' value '%v' not in enum [%s]", path, value, strings.Join(t.Values, ", ")),
			})
		}
	case "list":
		list, ok := value.([]any)
		if !ok {
			errors = append(errors, mismatch(path, "list", value))
			break
		}
		if t.ItemType == nil {
			break
		}
		for i, item := range list {
			errors = append(errors, checkType(*t.ItemType, item, fmt.Sprintf("%s[%d]", path, i))...)
		}
	case "object":
		m, ok := value.(map[string]any)
		if !ok {
			errors = append(errors, mismatch(path, "object", value))
			break
		}
		sub := &TypedSchema{ID: "", Fields: t.Fields}
		errors = append(errors, checkWithPrefix(sub, m, path)...)
	}
	return errors
}

func mismatch(path, want string, value any) TypeCheckError {
	return TypeCheckError{
		Code:    "type_mismatch",
		Field:   path,
		Message: fmt.Sprintf("Field '%s' expected %s, got %s", path, want, typeNameOf(value)),
	}
}

func typeNameOf(v any) string {
	switch v.(type) {
	case nil:
		return "null"
	case bool:
		return "boolean"
	case string:
		return "string"
	case []any:
		return "list"
	case map[string]any:
		return "object"
	}
	if isNumber(v) {
		return "number"
	}
	return fmt.Sprintf("%T", v)
}

func isNumber(v any) bool {
	switch v.(type) {
	case bool:
		return false
	case int, int8, int16, int32, int64,
		uint, uint8, uint16, uint32, uint64,
		float32, float64:
		return true
	}
	return false
}

func levenshtein(s1, s2 string) int {
	m, n := len(s1), len(s2)
	dp := make([]int, n+1)
	for i := 0; i <= n; i++ {
		dp[i] = i
	}
	for i := 1; i <= m; i++ {
		prev := dp[0]
		dp[0] = i
		for j := 1; j <= n; j++ {
			temp := dp[j]
			if s1[i-1] == s2[j-1] {
				dp[j] = prev
			} else {
				dp[j] = 1 + min3(prev, dp[j], dp[j-1])
			}
			prev = temp
		}
	}
	return dp[n]
}

func min3(a, b, c int) int {
	m := a
	if b < m {
		m = b
	}
	if c < m {
		m = c
	}
	return m
}
