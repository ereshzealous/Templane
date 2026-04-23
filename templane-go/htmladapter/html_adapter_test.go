package htmladapter

import (
	"strings"
	"testing"

	"templane-go/core"
)

func tir(nodes ...core.TIRNode) core.TIRResult {
	return core.TIRResult{TemplateID: "t", SchemaID: "s", Nodes: nodes}
}

func tirIDs(tmplID, schID string, nodes ...core.TIRNode) core.TIRResult {
	return core.TIRResult{TemplateID: tmplID, SchemaID: schID, Nodes: nodes}
}

func TestHtmlBasic(t *testing.T) {
	r := tirIDs("greeting", "user",
		core.TIRNode{Kind: "text", Content: "Hello "},
		core.TIRNode{Kind: "expr", Field: "name", Resolved: "Alice"},
		core.TIRNode{Kind: "text", Content: "!"},
	)
	got := Render(r)
	want := "<!-- templane template_id=greeting schema_id=user -->\nHello Alice!"
	if got != want {
		t.Errorf("got %q, want %q", got, want)
	}
}

func TestHtmlEscapesSpecialChars(t *testing.T) {
	r := tirIDs("escape", "data",
		core.TIRNode{Kind: "expr", Field: "content", Resolved: "<b>Hello & World</b>"},
	)
	got := Render(r)
	if !strings.Contains(got, "&lt;b&gt;Hello &amp; World&lt;/b&gt;") {
		t.Errorf("got %q", got)
	}
}

func TestHtmlDoesNotEscapeTextNodes(t *testing.T) {
	r := tir(core.TIRNode{Kind: "text", Content: "<li>item</li>"})
	got := Render(r)
	if !strings.Contains(got, "<li>item</li>") {
		t.Errorf("got %q", got)
	}
}

func TestHtmlProvenanceComment(t *testing.T) {
	r := tirIDs("my-template", "my-schema", core.TIRNode{Kind: "text", Content: "Hello"})
	got := Render(r)
	if !strings.HasPrefix(got, "<!-- templane template_id=my-template schema_id=my-schema -->") {
		t.Errorf("got %q", got)
	}
}

func TestHtmlFalsyZeroRendersAsString(t *testing.T) {
	r := tirIDs("counter", "stats",
		core.TIRNode{Kind: "text", Content: "Count: "},
		core.TIRNode{Kind: "expr", Field: "count", Resolved: 0},
	)
	got := Render(r)
	if !strings.Contains(got, "Count: 0") {
		t.Errorf("got %q", got)
	}
}

func TestHtmlNullResolvesToEmpty(t *testing.T) {
	r := tir(
		core.TIRNode{Kind: "text", Content: "X="},
		core.TIRNode{Kind: "expr", Field: "x", Resolved: nil},
	)
	got := Render(r)
	if !strings.Contains(got, "X=") {
		t.Errorf("got %q", got)
	}
	if strings.Contains(got, "null") || strings.Contains(got, "nil") {
		t.Errorf("should not contain null/nil literal: %q", got)
	}
}

func TestHtmlForeach(t *testing.T) {
	r := tirIDs("list", "data",
		core.TIRNode{Kind: "foreach", Var: "item", Items: [][]core.TIRNode{
			{
				{Kind: "text", Content: "<li>"},
				{Kind: "expr", Field: "item", Resolved: "apple"},
				{Kind: "text", Content: "</li>"},
			},
			{
				{Kind: "text", Content: "<li>"},
				{Kind: "expr", Field: "item", Resolved: "banana"},
				{Kind: "text", Content: "</li>"},
			},
		}},
	)
	got := Render(r)
	if !strings.Contains(got, "<li>apple</li><li>banana</li>") {
		t.Errorf("got %q", got)
	}
}
