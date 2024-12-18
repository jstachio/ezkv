/*
 * This code is heavily adapted from
 * https://github.com/Synt4xErr0r4/json5
 */
/*
 * MIT License
 *
 * Copyright (c) 2021 SyntaxError404
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.jstach.ezkv.json5.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.json5.internal.JSONParserOptions.DuplicateBehavior;
import io.jstach.ezkv.json5.internal.JSONValue.JSONArray;
import io.jstach.ezkv.json5.internal.JSONValue.JSONBoolean;
import io.jstach.ezkv.json5.internal.JSONValue.JSONNull;
import io.jstach.ezkv.json5.internal.JSONValue.JSONNumber;
import io.jstach.ezkv.json5.internal.JSONValue.JSONObject;
import io.jstach.ezkv.json5.internal.JSONValue.JSONString;

/**
 * A JSONParser is used to convert a source string into tokens, which then are used to
 * construct {@link JSONObject JSONObjects} and {@link JSONArray JSONArrays}
 *
 * @author SyntaxError404
 */
public class JSONParser {

	private final Reader reader;

	protected final JSONParserOptions options;

	/** whether the end of the file has been reached */
	private boolean eof;

	/** whether the current character should be re-read */
	private boolean back;

	/** the absolute position in the string */
	private long index;

	/** the relative position in the line */
	private long character;

	/** the line number */
	private long line;

	/** the previous character */
	private char previous;

	/** the current character */
	private char current;

	/**
	 * Constructs a new JSONParser from a Reader. The reader is not {@link Reader#close()
	 * closed}
	 * @param reader a reader
	 * @param options the options for parsing
	 * @since 1.1.0
	 */
	public JSONParser(Reader reader, JSONParserOptions options) {
		this.reader = reader.markSupported() ? reader : new BufferedReader(reader);

		this.options = Objects.requireNonNull(options);

		eof = false;
		back = false;

		index = -1;
		character = 0;
		line = 1;

		previous = 0;
		current = 0;
	}

	static JSONObject parseObject(JSONParser parser) {

		JSONObject obj = new JSONObject();
		var values = obj.values;

		char c;
		String key;

		if (parser.nextClean() != '{')
			throw parser.syntaxError("A JSONObject must begin with '{'");

		DuplicateBehavior duplicateBehavior = parser.options.duplicateBehaviour();

		// Set<String> duplicates = new HashSet<>();
		Map<String, JSONArray> duplicates = new LinkedHashMap<>();

		while (true) {
			c = parser.nextClean();

			switch (c) {
				case 0:
					throw parser.syntaxError("A JSONObject must end with '}'");
				case '}':
					return obj;
				default:
					parser.back();
					key = parser.nextMemberName();
			}

			boolean duplicate = obj.values.containsKey(key);

			if (duplicate && duplicateBehavior == DuplicateBehavior.UNIQUE)
				throw new JSONException("Duplicate key " + JSONStringify.quote(key));

			c = parser.nextClean();

			if (c != ':')
				throw parser.syntaxError("Expected ':' after a key, got '" + c + "' instead");

			var value = parser.nextValue();

			if (duplicate && duplicateBehavior == DuplicateBehavior.DUPLICATE) {
				JSONArray array = duplicates.get(key);
				if (array == null) {
					array = new JSONArray();
					array.values.add(obj.get(key));
					duplicates.put(key, array);
				}
				array.values.add(value);
				value = array;
			}

			values.put(key, value);

			c = parser.nextClean();

			if (c == '}')
				return obj;

			if (c != ',')
				throw parser.syntaxError("Expected ',' or '}' after value, got '" + c + "' instead");
		}
	}

	static JSONArray parseArray(JSONParser parser) {
		var array = new JSONArray();

		char c;

		if (parser.nextClean() != '[')
			throw parser.syntaxError("A JSONArray must begin with '['");

		while (true) {
			c = parser.nextClean();

			switch (c) {
				case 0:
					throw parser.syntaxError("A JSONArray must end with ']'");
				case ']':
					return array;
				default:
					parser.back();
			}

			JSONValue value = parser.nextValue();

			array.values.add(value);

			c = parser.nextClean();

			if (c == ']')
				return array;

			if (c != ',')
				throw parser.syntaxError("Expected ',' or ']' after value, got '" + c + "' instead");
		}
	}

	private boolean more() {
		if (back || eof)
			return back && !eof;

		return peek() > 0;
	}

	public boolean isEOF() {
		return eof;
	}

	/**
	 * Forces the parser to re-read the last character
	 */
	public void back() {
		back = true;
	}

	private char peek() {
		if (eof)
			return 0;

		int c;

		try {
			reader.mark(1);

			c = reader.read();

			reader.reset();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not peek from source", e);
		}

		return c == -1 ? 0 : (char) c;
	}

	private char next() {
		if (back) {
			back = false;
			return current;
		}

		int c;

		try {
			c = reader.read();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read from source", e);
		}

		if (c < 0) {
			eof = true;
			return 0;
		}

		previous = current;
		current = (char) c;

		index++;

		if (isLineTerminator(current) && (current != '\n' || (current == '\n' && previous != '\r'))) {
			line++;
			character = 0;
		}
		else
			character++;

		return current;
	}

	// https://262.ecma-international.org/5.1/#sec-7.3
	private boolean isLineTerminator(char c) {
		switch (c) {
			case '\n':
			case '\r':
			case 0x2028:
			case 0x2029:
				return true;
			default:
				return false;
		}
	}

	// https://spec.json5.org/#white-space
	private boolean isWhitespace(char c) {
		switch (c) {
			case '\t':
			case '\n':
			case 0x0B: // Vertical Tab
			case '\f':
			case '\r':
			case ' ':
			case 0xA0: // No-break space
			case 0x2028: // Line separator
			case 0x2029: // Paragraph separator
			case 0xFEFF: // Byte Order Mark
				return true;
			default:
				// Unicode category "Zs" (space separators)
				if (Character.getType(c) == Character.SPACE_SEPARATOR)
					return true;

				return false;
		}
	}

	// https://262.ecma-international.org/5.1/#sec-9.3.1
	private boolean isDecimalDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private void nextMultiLineComment() {
		while (true) {
			char n = next();

			if (n == '*' && peek() == '/') {
				next();
				return;
			}
		}
	}

	private void nextSingleLineComment() {
		while (true) {
			char n = next();

			if (isLineTerminator(n) || n == 0)
				return;
		}
	}

	/**
	 * Reads until encountering a character that is not a whitespace according to the
	 * <a href="https://spec.json5.org/#white-space">JSON5 Specification</a>
	 * @return a non-whitespace character, or {@code 0} if the end of the stream has been
	 * reached
	 */
	public char nextClean() {
		while (true) {
			if (!more()) {
				// throw syntaxError("Unexpected end of data");
				return 0;
			}

			char n = next();

			if (n == '/') {
				char p = peek();

				if (p == '*') {
					next();
					nextMultiLineComment();
				}

				else if (p == '/') {
					next();
					nextSingleLineComment();
				}
				else {
					return n;
				}
			}

			else if (!isWhitespace(n))
				return n;
		}
	}

	private String nextCleanTo(String delimiters) {
		StringBuilder result = new StringBuilder();

		while (true) {
			if (!more())
				throw syntaxError("Unexpected end of data");

			char n = nextClean();

			if (delimiters.indexOf(n) > -1 || isWhitespace(n)) {
				back();
				break;
			}

			result.append(n);
		}

		return result.toString();
	}

	private char[] unicodeEscape(boolean member, boolean part, char escapeChar) {

		// String escChar = switch(escapeType) {
		// case SHORT -> String.valueOf(escapeType.escapeChar);
		// case LONG -> throw syntaxError("Long unicode escape sequences are not
		// allowed");
		// };
		//
		// int numDigits = escapeType.digits;

		String escChar;
		int numDigits;
		switch (escapeChar) {
			case 'u' -> {
				escChar = "u";
				numDigits = 4;
			}
			case 'U' -> {
				throw syntaxError("Long unicode escape sequences are not allowed");
			}
			default -> {
				throw syntaxError("Invalid unicode escape char: " + escapeChar);
			}
		}

		String where = member ? "key" : "string";

		String value = "";
		int codepoint = 0;

		for (int i = 0; i < numDigits; ++i) {
			char n = next();
			value += n;

			int hex = dehex(n);

			if (hex == -1)
				throw syntaxError("Illegal unicode escape sequence '\\" + escChar + value + "' in " + where);

			codepoint |= hex << ((numDigits - i - 1) << 2);
		}

		if (member && !isMemberNameChar((char) codepoint, part))
			throw syntaxError("Illegal unicode escape sequence '\\" + escChar + value + "' in key");

		return Character.toChars(codepoint);
	}

	private void checkSurrogate(char hi, char lo) {
		if ((Character.isHighSurrogate(hi) && !Character.isSurrogate(lo))
				|| (!Character.isSurrogate(hi) && Character.isLowSurrogate(lo)))
			throw syntaxError(String.format("Invalid surrogate pair: U+%04X and U+%04X", (int) hi, (int) lo));
	}

	// https://spec.json5.org/#prod-JSON5String
	private String nextString(char quote) {
		StringBuilder result = new StringBuilder();

		String value;
		int codepoint;

		char n = 0;
		char prev;

		while (true) {
			if (!more())
				throw syntaxError("Unexpected end of data");

			prev = n;
			n = next();

			if (n == quote) {
				checkSurrogate(prev, (char) 0);
				break;
			}

			if (isLineTerminator(n) && n != 0x2028 && n != 0x2029) {
				throw syntaxError("Unescaped line terminator in string");
			}

			if (n == '\\') {
				n = next();

				if (isLineTerminator(n)) {
					if (n == '\r' && peek() == '\n')
						next();

					// escaped line terminator/ line continuation
					continue;
				}

				else
					switch (n) {
						case '\'':
						case '"':
						case '\\':
							result.append(n);
							continue;
						case 'b':
							result.append('\b');
							continue;
						case 'f':
							result.append('\f');
							continue;
						case 'n':
							result.append('\n');
							continue;
						case 'r':
							result.append('\r');
							continue;
						case 't':
							result.append('\t');
							continue;
						case 'v': // Vertical Tab
							result.append((char) 0x0B);
							continue;

						case '0': // NUL
							char p = peek();

							if (isDecimalDigit(p))
								throw syntaxError("Illegal escape sequence '\\0" + p + "'");

							result.append((char) 0);
							continue;

						case 'x': // Hex escape sequence
							value = "";
							codepoint = 0;

							for (int i = 0; i < 2; ++i) {
								n = next();
								value += n;

								int hex = dehex(n);

								if (hex == -1)
									throw syntaxError("Illegal hex escape sequence '\\x" + value + "' in string");

								codepoint |= hex << ((1 - i) << 2);
							}

							n = (char) codepoint;
							break;

						case 'u': // Unicode escape sequence (16-bit)
						case 'U': // Unicode escape sequence (32-bit)
							char[] chars = unicodeEscape(false, false, n);

							if (chars.length == 2) {
								checkSurrogate(prev, chars[0]);
								prev = chars[0];
								n = chars[1];

								result.append(prev);
							}
							else
								n = chars[0];

							break;

						default:
							if (isDecimalDigit(n)) {
								throw syntaxError("Illegal escape sequence '\\" + n + "'");
							}
							break;
					}
			}

			checkSurrogate(prev, n);

			result.append(n);
		}

		return result.toString();
	}

	private boolean isMemberNameChar(char n, boolean part) {
		if (n == '$' || n == '_' || n == 0x200C || n == 0x200D)
			return true;

		int type = Character.getType(n);

		switch (type) {
			case Character.UPPERCASE_LETTER:
			case Character.LOWERCASE_LETTER:
			case Character.TITLECASE_LETTER:
			case Character.MODIFIER_LETTER:
			case Character.OTHER_LETTER:
			case Character.LETTER_NUMBER:
				return true;

			case Character.NON_SPACING_MARK:
			case Character.COMBINING_SPACING_MARK:
			case Character.DECIMAL_DIGIT_NUMBER:
			case Character.CONNECTOR_PUNCTUATION:
				if (part)
					return true;
				break;
		}

		return false;
	}

	/**
	 * Reads a member name from the source according to the
	 * <a href="https://spec.json5.org/#prod-JSON5MemberName">JSON5 Specification</a>
	 * @return an member name
	 */
	public String nextMemberName() {
		StringBuilder result = new StringBuilder();

		char prev;
		char n = next();

		if (n == '"' || n == '\'')
			return nextString(n);

		back();
		n = 0;

		while (true) {
			if (!more())
				throw syntaxError("Unexpected end of data");

			boolean part = result.length() > 0;

			prev = n;
			n = next();

			if (n == '\\') { // unicode escape sequence
				n = next();

				if (n != 'u' && n != 'U')
					throw syntaxError("Illegal escape sequence '\\" + n + "' in key");

				char[] chars = unicodeEscape(true, part, n);

				if (chars.length == 2) {
					checkSurrogate(prev, chars[0]);
					prev = chars[0];
					n = chars[1];

					result.append(prev);
				}
				else
					n = chars[0];
			}
			else if (!isMemberNameChar(n, part)) {
				back();
				checkSurrogate(prev, (char) 0);
				break;
			}

			checkSurrogate(prev, n);

			result.append(n);
		}

		if (result.length() == 0)
			throw syntaxError("Empty key");

		return result.toString();
	}

	public JSONValue nextValue() {
		var value = nextValueOrNull();
		if (value == null) {
			throw syntaxError("Unexpected end of data");
		}
		return value;
	}

	public @Nullable JSONValue topValueOrNull() {
		var value = nextValueOrNull();
		if (value == null) {
			return null;
		}
		char n;
		if ((n = nextClean()) != 0) {
			throw syntaxError("Illegal value '" + n + "'");
		}
		return value;
	}

	/**
	 * Reads a value from the source according to the
	 * <a href="https://spec.json5.org/#prod-JSON5Value">JSON5 Specification</a>
	 * @return an member name
	 */
	public @Nullable JSONValue nextValueOrNull() {
		char n = nextClean();
		switch (n) {
			case '"', '\'':
				return new JSONString(nextString(n));
			case '{':
				back();
				return parseObject(this);
			case '[':
				back();
				return parseArray(this);
			case 0:
				return null;
		}

		back();

		final String string = nextCleanTo(",]}");

		if (string.equals("null"))
			return JSONNull.NULL;

		if (string.equals("true"))
			return JSONBoolean.TRUE;

		if (string.equals("false"))
			return JSONBoolean.FALSE;

		if (!string.isEmpty()) {
			char leading = string.charAt(0);
			String rest = string;

			double sign = 1;

			if (leading == '+') {
				rest = string.substring(1);
			}
			else if (leading == '-') {
				rest = string.substring(1);
				sign = -1;
			}

			if (rest.equals("Infinity")) {
				return new JSONNumber(Math.copySign(Double.POSITIVE_INFINITY, sign), string);
			}

			if (rest.equals("NaN")) {
				return new JSONNumber(Math.copySign(Double.NaN, sign), string);
			}

			if (!rest.isEmpty()) {
				leading = rest.charAt(0);

				if ((leading >= '0' && leading <= '9') || leading == '.') {
					Number num = parseNumber(leading, rest);

					if (sign < 0) {
						if (num instanceof BigInteger bi) {
							return new JSONNumber(bi.negate(), string);
						}
						else if (num instanceof BigDecimal bd) {
							return new JSONNumber(bd.negate(), string);
						}
					}

					return new JSONNumber(num, string);
				}
			}
		}

		String errorValue = string.isEmpty() ? "" + n : string;
		throw syntaxError("Illegal value '" + errorValue + "'");
	}

	private Number parseNumber(char leading, String input) {
		@NonNull
		BigInteger intValue = BigInteger.ZERO;

		int n = input.length();
		boolean floating = false;
		boolean hex = false;
		int off = 0;
		char c = 0;

		if (leading == '0') {
			if (n == 1) {
				return intValue;
			}

			/*
			 * PREFIXES
			 */
			switch (c = input.charAt(1)) {
				case 'b', 'B':
					throw syntaxError("Binary literals are not allowed");

				// /**********
				// * BINARY *
				// **********/
				// case 'b':
				// case 'B':
				// if (!options.allowBinaryLiterals())
				// throw syntaxError("Binary literals are not allowed");
				//
				// off = 2;
				//
				// while (off < n) {
				// c = input.charAt(off++);
				//
				// if (checkDigitSeparator(c)) {
				// if (off == 3 || off >= n || !isbin(input.charAt(off)))
				// throw syntaxError("Illegal position for digit separator");
				//
				// continue;
				// }
				//
				// if (!isbin(c))
				// throw syntaxError("Expected binary digit for literal");
				//
				// intValue = intValue.shiftLeft(1);
				//
				// if (c == '1')
				// intValue = intValue.setBit(0);
				// }
				//
				// if (off == 2)
				// throw syntaxError("Expected binary digit after '0b'");
				//
				// return intValue;
				case 'o', 'O':
					throw syntaxError("Octal literals are not allowed");

				// /*********
				// * OCTAL *
				// *********/
				// case 'o':
				// case 'O':
				// if (!options.allowOctalLiterals())
				// throw syntaxError("Octal literals are not allowed");
				//
				// off = 2;
				//
				// while (off < n) {
				// c = input.charAt(off++);
				//
				// if (checkDigitSeparator(c)) {
				// if (off == 3 || off >= n || !isoct(input.charAt(off)))
				// throw syntaxError("Illegal position for digit separator");
				//
				// continue;
				// }
				//
				// if (!isoct(c))
				// throw syntaxError("Expected octal digit for literal");
				//
				// intValue = intValue.shiftLeft(3);
				//
				// if (c != '0')
				// intValue = intValue.or(BigInteger.valueOf(c - '0'));
				// }
				//
				// if (off == 2)
				// throw syntaxError("Expected octal digit after '0o'");
				//
				// return intValue;

				/*
				 * HEXADECIMAL
				 */
				case 'x', 'X':
					off = 2;
					hex = true;

					while (off < n) {
						c = input.charAt(off++);

						if (checkDigitSeparator(c)) {
							if (off == 3 || off >= n || !ishex(input.charAt(off)))
								throw syntaxError("Illegal position for digit separator");

							continue;
						}

						if (c == '.' || c == 'p' || c == 'P') {
							// if (!options.isFlag(JSONParserFlag.HEX_FLOATING_LITERALS))
							throw syntaxError("Hexadecimal floating-point literals are not allowed");

							// floating = true;
							// break;
						}

						if (!ishex(c))
							throw syntaxError("Expected hexadecimal digit for literal");

						intValue = intValue.shiftLeft(4);

						if (c != '0')
							intValue = intValue.or(BigInteger.valueOf(dehex(c)));
					}

					if (off == 2)
						throw syntaxError("Expected hexadecimal digit after '0x'");

					if (!floating)
						return intValue;

					break;

				default:
					break;
			}
			;
		}

		StringBuilder num = new StringBuilder();

		if (!hex) {
			/*
			 * DECIMAL
			 */
			while (off < n) {
				c = input.charAt(off++);

				if (checkDigitSeparator(c)) {
					if (num.length() == 0 || off >= n || !isDecimalDigit(input.charAt(off)))
						throw syntaxError("Illegal position for digit separator");

					continue;
				}

				if (c == '.' || c == 'e' || c == 'E') {
					floating = true;
					break;
				}

				if (!isDecimalDigit(c))
					throw syntaxError("Expected decimal digit for literal");

				num.append(c);
			}

			if (off >= n) {
				// if (options.allowJavaDigitSeparators())
				// input = input.replace("_", "");
				//
				// if (options.allowCDigitSeparators())
				// input = input.replace("'", "");

				return new BigInteger(input);
			}
		}

		BigInteger fractionInt = BigInteger.ZERO;
		int numFracDigits = 0;

		if (c == '.') {
			/*
			 * FRACTION
			 */
			if (!hex)
				num.append('.');

			while (off < n) {
				c = input.charAt(off++);

				if (checkDigitSeparator(c)) {
					if (numFracDigits == 0 || off >= n)
						throw syntaxError("Illegal position for digit separator");

					c = input.charAt(off);

					if ((!hex && !isDecimalDigit(c)) || (hex && !ishex(c)))
						throw syntaxError("Illegal position for digit separator");

					continue;
				}

				if (hex) {
					if (c == 'p' || c == 'P')
						break;

					if (!ishex(c))
						throw syntaxError("Expected hexadecimal digit for literal");

					fractionInt = fractionInt.shiftLeft(4);

					if (c != '0')
						fractionInt = fractionInt.or(BigInteger.valueOf(dehex(c)));
				}
				else {
					if (c == 'e' || c == 'E')
						break;

					if (!isDecimalDigit(c))
						throw syntaxError("Expected decimal digit for literal");

					num.append(c);
				}

				++numFracDigits;
			}

			if (off >= n && !hex) {
				return new BigDecimal(num.toString());
			}
		}

		/*
		 * EXPONENT
		 */
		if (hex && c != 'p' && c != 'P')
			throw syntaxError("Expected exponent for hexadecimal floating-point literal");

		if (!hex)
			num.append('e');

		int numExpDigits = 0;

		if (++off >= n)
			throw syntaxError("Expected digit sequence for exponent");

		c = input.charAt(off);

		if (c == '+' || c == '-') {
			num.append(c);
			++off;
		}

		while (off < n) {
			c = input.charAt(off++);

			if (checkDigitSeparator(c)) {
				if (numExpDigits == 0 || off >= n || !isDecimalDigit(input.charAt(off))) {
					throw syntaxError("Illegal position for digit separator");
				}

				continue;
			}

			if (!isDecimalDigit(c)) {
				throw syntaxError("Expected decimal digit for exponent");
			}

			num.append(c);
			++numExpDigits;
		}

		if (numExpDigits == 0) {
			throw syntaxError("Expected digit sequence for exponent");
		}

		if (!hex) {
			return new BigDecimal(num.toString());
		}

		// TODO remove below as we do not support hexadecimal floating point

		/*
		 * HEXADECIMAL FLOATING-POINT
		 */
		BigInteger exponent = new BigInteger(num.toString());
		BigDecimal value = new BigDecimal(intValue);

		BigDecimal two = BigDecimal.valueOf(2);
		BigDecimal frac = BigDecimal.valueOf(.5);

		for (int i = (4 * numFracDigits) - 1; i >= 0; --i) {
			if (fractionInt.testBit(i)) {
				value = value.add(frac);
			}

			frac = frac.divide(two);
		}

		BigDecimal scale;

		try {
			scale = new BigDecimal(BigInteger.TWO.pow(exponent.intValueExact()));
		}
		catch (Exception e) {
			throw syntaxError("Hexadecimal floating-point literal's exponent is too large");
		}

		return value.multiply(scale);
	}

	private boolean checkDigitSeparator(char c) {
		if (c == '_') {
			// if (!options.allowJavaDigitSeparators())
			throw syntaxError("Java-style digit separators are not allowed");

			// return true;
		}

		if (c == '\'') {
			// if (!options.allowCDigitSeparators())
			throw syntaxError("C-style digit separators are not allowed");

			// return true;
		}

		return false;
	}

	/**
	 * Constructs a new JSONException with a detail message and a causing exception
	 * @param message the detail message
	 * @param cause the causing exception
	 * @return a JSONException
	 */
	public JSONException syntaxError(String message, Throwable cause) {
		return new JSONException(message + this, cause);
	}

	/**
	 * Constructs a new JSONException with a detail message
	 * @param message the detail message
	 * @return a JSONException
	 */
	public JSONException syntaxError(String message) {
		return new JSONException(message + this);
	}

	@Override
	public String toString() {
		return " at index " + index + " [character " + character + " in line " + line + "]";
	}

	private static int dehex(char c) {
		if (c >= '0' && c <= '9')
			return c - '0';

		if (c >= 'a' && c <= 'f')
			return c - 'a' + 0xA;

		if (c >= 'A' && c <= 'F')
			return c - 'A' + 0xA;

		return -1;
	}

	// private static boolean isbin(char c) {
	// return c == '0' || c == '1';
	// }
	//
	// private static boolean isoct(char c) {
	// return c >= '0' && c <= '7';
	// }

	private static boolean ishex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

}
