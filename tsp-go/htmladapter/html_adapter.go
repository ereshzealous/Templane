// Package htmladapter renders a TSP TIR to HTML with expr-value escaping.
package htmladapter

import (
	"fmt"
	"strings"

	"tsp-go/core"
)

// Render produces an HTML string from a TIR.
// Text nodes are emitted as-is; expression values are HTML-entity-escaped.
func Render(tir core.TIRResult) string {
	var sb strings.Builder
	sb.WriteString("<!-- tsp template_id=")
	sb.WriteString(tir.TemplateID)
	sb.WriteString(" schema_id=")
	sb.WriteString(tir.SchemaID)
	sb.WriteString(" -->\n")
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
		return escape(fmt.Sprintf("%v", n.Resolved))
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

// escape applies HTML entity escaping to the five characters covered by
// Python's html.escape(s, quote=True): &, <, >, ", '.
func escape(s string) string {
	s = strings.ReplaceAll(s, "&", "&amp;")
	s = strings.ReplaceAll(s, "<", "&lt;")
	s = strings.ReplaceAll(s, ">", "&gt;")
	s = strings.ReplaceAll(s, `"`, "&quot;")
	s = strings.ReplaceAll(s, "'", "&#x27;")
	return s
}
