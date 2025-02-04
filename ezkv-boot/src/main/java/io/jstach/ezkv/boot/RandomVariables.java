package io.jstach.ezkv.boot;

import java.util.HexFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesEnvironment;
import io.jstach.ezkv.kvs.Variables;

// This code is heavily inspired by Spring Boot RandomValuePropertySource.
// https://docs.spring.io/spring-boot/api/java/org/springframework/boot/env/RandomValuePropertySource.html
// SPDX-License-Identifier: Apache-2.0
record RandomVariables(Random source, KeyValuesEnvironment.Logger logger, String prefix) implements Variables {

	public static RandomVariables of(KeyValuesEnvironment environment) {
		var source = environment.getRandom();
		var logger = environment.getLogger();
		String prefix = "random.";

		return new RandomVariables(source, logger, prefix);
	}

	@Override
	public @Nullable String getValue(String key) {
		if (!key.startsWith(prefix)) {
			return null;
		}
		if (logger.isDebug()) {
			logger.debug(String.format("Generating random property for '%s'", key));
		}
		return "" + getRandomValue(key.substring(prefix.length()));
	}

	private Object getRandomValue(String type) {
		if (type.equals("int")) {
			return source.nextInt();
		}
		if (type.equals("long")) {
			return source.nextLong();
		}
		String range = getRange(type, "int");
		if (range != null) {
			return getNextIntInRange(Range.of(range, Integer::parseInt));
		}
		range = getRange(type, "long");
		if (range != null) {
			return getNextLongInRange(Range.of(range, Long::parseLong));
		}
		if (type.equals("uuid")) {
			return UUID.randomUUID().toString();
		}
		return getRandomBytes();
	}

	private @Nullable String getRange(String type, String prefix) {
		if (type.startsWith(prefix)) {
			int startIndex = prefix.length() + 1;
			if (type.length() > startIndex) {
				return type.substring(startIndex, type.length() - 1);
			}
		}
		return null;
	}

	private Object getRandomBytes() {
		byte[] bytes = new byte[16];
		source.nextBytes(bytes);
		return HexFormat.of().withLowerCase().formatHex(bytes);
	}

	private int getNextIntInRange(Range<Integer> range) {
		OptionalInt first = source.ints(1, range.getMin(), range.getMax()).findFirst();
		assertPresent(first.isPresent(), range);
		return first.getAsInt();
	}

	private long getNextLongInRange(Range<Long> range) {
		OptionalLong first = source.longs(1, range.getMin(), range.getMax()).findFirst();
		assertPresent(first.isPresent(), range);
		return first.getAsLong();
	}

	private void assertPresent(boolean present, Range<?> range) {
		if (!present) {
			throw new IllegalStateException("Could not get random number for range '" + range + "'");
		}
	}

	private static List<String> parseCSV(String csv) {
		return Stream.of(csv.split(",")).map(s -> s.trim()).filter(s -> !s.isBlank()).toList();
	}

	static final class Range<T extends Number> {

		private final String value;

		private final T min;

		private final T max;

		private Range(String value, T min, T max) {
			this.value = value;
			this.min = min;
			this.max = max;
		}

		T getMin() {
			return this.min;
		}

		T getMax() {
			return this.max;
		}

		@Override
		public String toString() {
			return this.value;
		}

		static <T extends Number & Comparable<T>> Range<T> of(String value, Function<String, T> parse) {
			T zero = parse.apply("0");
			List<String> tokens = parseCSV(value);
			T min = parse.apply(tokens.getFirst());
			if (tokens.size() == 1) {
				if (min.compareTo(zero) <= 0) {
					throw new IllegalArgumentException("Bound must be positive.");
				}
				return new Range<>(value, zero, min);
			}
			T max = parse.apply(tokens.get(1));
			if (min.compareTo(max) > 0) {
				throw new IllegalArgumentException("Lower bound must be less than upper bound.");
			}
			return new Range<>(value, min, max);
		}

	}

}