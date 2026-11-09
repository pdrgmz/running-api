package com.running.api.mcp;

/**
 * Resultado de una herramienta MCP. {@code isError=true} indica al agente que
 * debe corregir la llamada; no es un error de transporte JSON-RPC.
 */
public record McpToolResult(String text, boolean isError) {

    public static McpToolResult success(String text) {
        return new McpToolResult(text, false);
    }

    public static McpToolResult error(String text) {
        return new McpToolResult(text, true);
    }
}
