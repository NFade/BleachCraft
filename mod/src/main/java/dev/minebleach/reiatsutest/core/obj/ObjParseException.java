package dev.minebleach.reiatsutest.core.obj;

public class ObjParseException extends RuntimeException {
	public ObjParseException(String source, int line, String message) {
		super(source + ":" + line + ": " + message);
	}
}
