package core

import (
	"reflect"
	"testing"
)

func TestBasicExpr(t *testing.T) {
	ast := []ASTNode{
		{Kind: "text", Content: "Hello "},
		{Kind: "expr", Field: "name"},
		{Kind: "text", Content: "!"},
	}
	r := Generate(ast, map[string]any{"name": "Alice"}, "user", "greeting")
	want := TIRNode{Kind: "expr", Field: "name", Resolved: "Alice"}
	if !reflect.DeepEqual(r.Nodes[1], want) {
		t.Errorf("got %+v, want %+v", r.Nodes[1], want)
	}
}

func TestMissingPathResolvesToNil(t *testing.T) {
	r := Generate([]ASTNode{{Kind: "expr", Field: "missing"}}, map[string]any{}, "s", "t")
	if r.Nodes[0].Resolved != nil {
		t.Errorf("resolved = %v, want nil", r.Nodes[0].Resolved)
	}
}

func TestIfTruePicksThenBranch(t *testing.T) {
	ast := []ASTNode{{
		Kind:       "if",
		Condition:  &Condition{Op: "==", Left: "status", Right: "active"},
		ThenBranch: []ASTNode{{Kind: "text", Content: "Active"}},
		ElseBranch: []ASTNode{{Kind: "text", Content: "Inactive"}},
	}}
	r := Generate(ast, map[string]any{"status": "active"}, "s", "t")
	if r.Nodes[0].Kind != "if" || !r.Nodes[0].Condition {
		t.Fatalf("expected if=true, got %+v", r.Nodes[0])
	}
	if len(r.Nodes[0].Branch) != 1 || r.Nodes[0].Branch[0].Content != "Active" {
		t.Errorf("branch = %+v", r.Nodes[0].Branch)
	}
}

func TestIfFalsePicksElseBranch(t *testing.T) {
	ast := []ASTNode{{
		Kind:       "if",
		Condition:  &Condition{Op: "==", Left: "status", Right: "active"},
		ThenBranch: []ASTNode{{Kind: "text", Content: "Active"}},
		ElseBranch: []ASTNode{},
	}}
	r := Generate(ast, map[string]any{"status": "inactive"}, "s", "t")
	if r.Nodes[0].Condition {
		t.Errorf("condition should be false")
	}
	if len(r.Nodes[0].Branch) != 0 {
		t.Errorf("branch should be empty, got %+v", r.Nodes[0].Branch)
	}
}

func TestForeachRendersEachItem(t *testing.T) {
	ast := []ASTNode{{
		Kind:     "foreach",
		Var:      "tag",
		Iterable: "tags",
		Body:     []ASTNode{{Kind: "expr", Field: "tag"}},
	}}
	r := Generate(ast, map[string]any{"tags": []any{"py", "ts", "java"}}, "s", "t")
	if len(r.Nodes[0].Items) != 3 {
		t.Fatalf("got %d items", len(r.Nodes[0].Items))
	}
	first := r.Nodes[0].Items[0][0]
	if first.Field != "tag" || first.Resolved != "py" {
		t.Errorf("unexpected item: %+v", first)
	}
}

func TestNestedDottedPath(t *testing.T) {
	ast := []ASTNode{{Kind: "expr", Field: "user.address.city"}}
	r := Generate(ast, map[string]any{
		"user": map[string]any{"address": map[string]any{"city": "London"}},
	}, "s", "t")
	if r.Nodes[0].Resolved != "London" {
		t.Errorf("resolved = %v", r.Nodes[0].Resolved)
	}
}

func TestNestedPathMissingSegmentReturnsNil(t *testing.T) {
	ast := []ASTNode{{Kind: "expr", Field: "user.address.city"}}
	r := Generate(ast, map[string]any{"user": map[string]any{}}, "s", "t")
	if r.Nodes[0].Resolved != nil {
		t.Errorf("resolved = %v, want nil", r.Nodes[0].Resolved)
	}
}

func TestConditionEquals(t *testing.T) {
	ast := []ASTNode{{
		Kind:       "if",
		Condition:  &Condition{Op: "==", Left: "score", Right: "100"},
		ThenBranch: []ASTNode{{Kind: "text", Content: "Perfect"}},
		ElseBranch: []ASTNode{},
	}}
	r := Generate(ast, map[string]any{"score": "100"}, "s", "t")
	if !r.Nodes[0].Condition {
		t.Errorf("expected true")
	}
}

func TestProvenance(t *testing.T) {
	r := Generate([]ASTNode{}, map[string]any{}, "my-schema", "my-template")
	if r.SchemaID != "my-schema" || r.TemplateID != "my-template" {
		t.Errorf("unexpected provenance: %+v", r)
	}
}
