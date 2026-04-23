// Package yamladapter renders a Templane TIR to YAML (no escaping).
package yamladapter

import (
	"fmt"
	"strings"

	"templane-go/core"
)

// Render produces a YAML string from a TIR.
// Values are rendered with no escaping (YAML's own quoting rules apply at the serializer level, not here).
func Render(tir core.TIRResult) string {
	var sb strings.Builder
	sb.WriteString("# templane template_id=")
	sb.WriteString(tir.TemplateID)
	sb.WriteString(" schema_id=")
	sb.WriteString(tir.SchemaID)
	sb.WriteString("\n")
	for _, n := range tir.Nodes {
		sb.WriteString(renderNode(n))
	}
	return sb.String()
}

func renderNode(n core.TIRNode) string {
	switch n.Kind {
	case "text":
		return n.Content
	case "expr":
		if n.Resolved == nil {
			return ""
		}
		return fmt.Sprintf("%v", n.Resolved)
	case "if":
		var sb strings.Builder
		for _, b := range n.Branch {
			sb.WriteString(renderNode(b))
		}
		return sb.String()
	case "foreach":
		var sb strings.Builder
		for _, item := range n.Items {
			for _, b := range item {
				sb.WriteString(renderNode(b))
			}
		}
		return sb.String()
	}
	return ""
}
