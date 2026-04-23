package core

import (
	"fmt"
	"strings"
)

// Generate walks an AST with data, producing a Typed Intermediate Representation.
// Missing paths resolve to nil; only "==" conditions are supported.
func Generate(ast []ASTNode, data map[string]any, schemaID, templateID string) TIRResult {
	nodes := make([]TIRNode, 0, len(ast))
	for _, n := range ast {
		nodes = append(nodes, nodeToTIR(n, data))
	}
	return TIRResult{TemplateID: templateID, SchemaID: schemaID, Nodes: nodes}
}

func nodeToTIR(node ASTNode, data map[string]any) TIRNode {
	switch node.Kind {
	case "text":
		return TIRNode{Kind: "text", Content: node.Content}
	case "expr":
		return TIRNode{Kind: "expr", Field: node.Field, Resolved: resolve(data, node.Field)}
	case "if":
		var cond bool
		if node.Condition != nil {
			cond = evaluate(*node.Condition, data)
		}
		var branch []ASTNode
		if cond {
			branch = node.ThenBranch
		} else {
			branch = node.ElseBranch
		}
		out := make([]TIRNode, 0, len(branch))
		for _, b := range branch {
			out = append(out, nodeToTIR(b, data))
		}
		return TIRNode{Kind: "if", Condition: cond, Branch: out}
	case "foreach":
		itemsVal := resolve(data, node.Iterable)
		items, _ := itemsVal.([]any)
		rendered := make([][]TIRNode, 0, len(items))
		for _, item := range items {
			scope := make(map[string]any, len(data)+1)
			for k, v := range data {
				scope[k] = v
			}
			scope[node.Var] = item
			body := make([]TIRNode, 0, len(node.Body))
			for _, b := range node.Body {
				body = append(body, nodeToTIR(b, scope))
			}
			rendered = append(rendered, body)
		}
		return TIRNode{Kind: "foreach", Var: node.Var, Items: rendered}
	}
	return TIRNode{}
}

func resolve(data map[string]any, path string) any {
	var current any = data
	for _, part := range strings.Split(path, ".") {
		m, ok := current.(map[string]any)
		if !ok {
			return nil
		}
		v, present := m[part]
		if !present {
			return nil
		}
		current = v
	}
	return current
}

func evaluate(c Condition, data map[string]any) bool {
	if c.Op == "==" {
		return fmt.Sprintf("%v", resolve(data, c.Left)) == fmt.Sprintf("%v", c.Right)
	}
	return false
}
