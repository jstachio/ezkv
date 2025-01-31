package io.jstach.ezkv.kvs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.SedParserException.ErrorType;

final class DefaultSedParser {

	static Command parseCommand(String input) throws SedParserException {
		Tokenizer tokenizer = new Tokenizer(input);
		SedParser parser = new SedParser(tokenizer.iterator());
		return parser.parse();
	}

	static Command parse(String input) throws SedParserException {
		try {
			return parseCommand(input);
		}
		catch (SedParserException e) {
			throw new SedParserException(e, input);
		}
	}

	enum TokenType {

		ADDRESS, COMMAND, DELIMITER, PATTERN, REPLACEMENT, FLAG, EOF

	}

	record Token(TokenType type, String value) {
		static boolean isType(@Nullable Token token, TokenType type) {
			if (token == null) {
				return false;
			}
			return token.type == type;
		}
	}

	record Address(Pattern regex) {

		public static Address of(String regex) {
			return new Address(Pattern.compile(regex));
		}

		public boolean matches(String key) {
			return regex.matcher(key).find();
		}
	}

	static class Tokenizer implements Iterable<Token> {

		private final String input;

		private int position = 0;

		public Tokenizer(String input) {
			this.input = input;
		}

		public List<Token> tokenize() {
			List<Token> tokens = new ArrayList<>();
			position = 0;

			Character currentDelimiter = null;
			Token lastToken = null;
			while (position < input.length()) {
				char currentChar = input.charAt(position);
				if (lastToken == null && currentChar == '/') {
					lastToken = parseRegexAddress();
					tokens.add(lastToken);
				}
				else if (isCommandChar(currentChar)
						&& (Token.isType(lastToken, TokenType.ADDRESS) || lastToken == null)) {
					lastToken = new Token(TokenType.COMMAND, String.valueOf(currentChar));
					tokens.add(lastToken);
					position++;
				}
				else if (currentDelimiter == null && isDelimiter(currentChar)) {
					currentDelimiter = currentChar;
					lastToken = new Token(TokenType.DELIMITER, String.valueOf(currentChar));
					tokens.add(lastToken);
					position++;
				}
				else if (currentDelimiter != null && currentDelimiter.equals(currentChar)) {
					lastToken = new Token(TokenType.DELIMITER, String.valueOf(currentChar));
					tokens.add(lastToken);
					position++;
				}
				else if (isFlagChar(currentChar) && position == input.length() - 1) {
					lastToken = new Token(TokenType.FLAG, String.valueOf(currentChar));
					tokens.add(lastToken);
					position++;
				}
				else if (currentDelimiter == null) {
					// not sure on this.
					position++;
					continue;
				}
				else {
					String value = readUntilDelimiter(currentDelimiter);
					lastToken = new Token(TokenType.PATTERN, value);
					tokens.add(lastToken);
				}
			}

			tokens.add(new Token(TokenType.EOF, ""));
			return tokens;
		}

		private static boolean isCommandChar(char c) {
			return Character.isAlphabetic(c);
		}

		private static boolean isFlagChar(char c) {
			return Character.isAlphabetic(c);
		}

		@Override
		public Iterator<Token> iterator() {
			return tokenize().iterator();
		}

		private Token parseRegexAddress() {
			StringBuilder regexBuilder = new StringBuilder();
			position++; // Skip the initial '/'

			while (position < input.length()) {
				char currentChar = input.charAt(position);
				if (currentChar == '/') {
					position++; // Skip the closing '/'
					return new Token(TokenType.ADDRESS, regexBuilder.toString());
				}
				if (currentChar == '\\' && position + 1 < input.length() && input.charAt(position + 1) == '/') {
					regexBuilder.append('/');
					position += 2;
				}
				else {
					regexBuilder.append(currentChar);
					position++;
				}
			}

			throw new SedParserException(ErrorType.INVALID_REGEX_ADDRESS, "Unterminated regex address");
		}

		private boolean isDelimiter(char c) {
			return c != '\\' && c != '\n' && !Character.isWhitespace(c);
		}

		private String readUntilDelimiter(char delimiter) {
			StringBuilder value = new StringBuilder();
			while (position < input.length()) {
				char currentChar = input.charAt(position);
				if (currentChar == delimiter) {
					break;
				}
				if (currentChar == '\\' && position + 1 < input.length() && input.charAt(position + 1) == delimiter) {
					value.append(delimiter);
					position += 2;
				}
				else {
					value.append(currentChar);
					position++;
				}
			}
			return value.toString();
		}

	}

	// enum ResultType {
	// DELETE,
	// IGNORE,
	// UPDATE
	// }
	// record Result(ResultType type, String value) {}

	sealed interface Command permits SubstitutionCommand, DeleteCommand {

		@Nullable
		String execute(String key);

	}

	record SubstitutionCommand(@Nullable Address address, String pattern, String replacement,
			boolean isGlobal) implements Command {
		@Override
		public @Nullable String execute(String key) {
			var address = this.address;
			if (address == null || address.matches(key)) {
				return isGlobal ? key.replaceAll(pattern, replacement) : key.replaceFirst(pattern, replacement);
			}
			return key; // If the address does not match, return the original key
		}
	}

	record DeleteCommand(@Nullable Address address) implements Command {
		@Override
		public @Nullable String execute(String key) {
			var address = this.address;
			if (address == null || address.matches(key)) {
				return null; // Indicate that the key should be deleted
			}
			return key; // Return the original key if not matched
		}
	}

	static class SedParser {

		private final Iterator<Token> tokenIterator;

		private Token currentToken;

		public SedParser(Iterator<Token> tokenIterator) {
			this.tokenIterator = tokenIterator;
			currentToken = advance(tokenIterator); // Initialize with the first token
		}

		private static Token advance(Iterator<Token> tokenIterator) {
			if (tokenIterator.hasNext()) {
				return tokenIterator.next();
			}
			else {
				return new Token(TokenType.EOF, "");
			}
		}

		private void advance() {
			currentToken = advance(tokenIterator);
		}

		public Command parse() {
			Address address = null;

			// Check if the current token is an address (e.g., /match/)
			if (currentToken.type() == TokenType.ADDRESS) {
				address = Address.of(currentToken.value());
				advance(); // Move to the next token, which should be the command
			}

			// Parse the function (e.g., substitution or delete command)
			if (currentToken.type() == TokenType.COMMAND) {
				switch (currentToken.value()) {
					case "s" -> {
						advance(); // Move past the 's' command
						return parseSubstitution(address);
					}
					case "d" -> {
						advance(); // Move past the 'd' command
						return parseDelete(address);
					}
					default -> throw new SedParserException(ErrorType.INVALID_COMMAND,
							"Unsupported command: " + currentToken.value());
				}
			}
			if (address != null) {
				throw new SedParserException(ErrorType.COMMAND_EXPECTED, "Command expected");
			}

			throw new SedParserException(ErrorType.INVALID_SYNTAX, "Invalid syntax");
		}

		private SubstitutionCommand parseSubstitution(@Nullable Address address) {
			if (currentToken.type() != TokenType.DELIMITER) {
				throw new SedParserException(ErrorType.BUG, "Expected delimiter after 's'");
			}
			char delimiter = currentToken.value().charAt(0);
			advance(); // Move past the delimiter

			String pattern = readUntilDelimiter(delimiter);
			String replacement = readUntilDelimiter(delimiter);

			boolean isGlobal = false;
			if (currentToken.type() == TokenType.FLAG) {
				var v = currentToken.value();
				switch (v) {
					case "g" -> {
						isGlobal = true;
						advance();
					}
					default -> {
						throw new SedParserException(ErrorType.INVALID_FLAG, "Invalid flag. flag='" + v + "'");
					}
				}
			}

			return new SubstitutionCommand(address, pattern, replacement, isGlobal);
		}

		private DeleteCommand parseDelete(@Nullable Address address) {
			return new DeleteCommand(address); // No further parsing needed for the 'd'
												// command
		}

		private String readUntilDelimiter(char delimiter) {
			StringBuilder value = new StringBuilder();
			while (currentToken.type() != TokenType.DELIMITER && currentToken.type() != TokenType.EOF) {
				value.append(currentToken.value());
				advance();
			}
			if (currentToken.type() == TokenType.DELIMITER && currentToken.value().charAt(0) == delimiter) {
				advance(); // Move past the delimiter
			}
			else {
				throw new SedParserException(ErrorType.MISSING_CLOSING_DELIMITER, "Expected closing delimiter");
			}
			return value.toString();
		}

	}

}

class SedParserException extends IllegalArgumentException {

	private static final long serialVersionUID = 7757638249767951898L;

	enum ErrorType {

		INVALID_COMMAND, //
		MISSING_CLOSING_DELIMITER, //
		BUG, //
		INVALID_SYNTAX, //
		INVALID_REGEX_ADDRESS, //
		COMMAND_EXPECTED, //
		INVALID_FLAG;

	}

	final ErrorType error;

	public SedParserException(ErrorType error, String s) {
		super(s);
		this.error = error;
	}

	public SedParserException(SedParserException exception, String input) {
		super("Error for input='" + input + "'", exception);
		this.error = exception.error;
	}

}
