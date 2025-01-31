package io.jstach.ezkv.kvs;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.jstach.ezkv.kvs.DefaultSedParser.Command;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter;

@SuppressWarnings("EnumOrdinal")
enum DefaultKeyValuesFilter implements KeyValuesFilter {

	GREP(KeyValuesResource.FILTER_GREP) {

		@Override
		protected KeyValues doFilter(FilterContext context, KeyValues keyValues, String expression, Target target) {
			String grep = expression;
			// TODO error handling.
			Objects.requireNonNull(keyValues);
			Pattern pattern = Pattern.compile(grep);
			return keyValues.filter(kv -> {
				if (context.keyValueIgnore().test(kv)) {
					return true;
				}
				String v = switch (target) {
					case KEY, DEFAULT -> kv.key();
					case VALUE -> kv.value();
				};
				return pattern.matcher(v).find();
			});
		}
	},
	SED(KeyValuesResource.FILTER_SED) {

		@Override
		protected KeyValues doFilter(FilterContext context, KeyValues keyValues, String expression, Target target) {
			String sed = expression;
			Objects.requireNonNull(sed);
			var command = DefaultSedParser.parse(sed);
			var ignorePredicate = context.keyValueIgnore();
			return keyValues.flatMap(kv -> forKeyValue(target, command, kv, ignorePredicate));
		}

		private KeyValues forKeyValue(Target target, Command command, KeyValue kv,
				Predicate<KeyValue> ignorePredicate) {
			if (ignorePredicate.test(kv)) {
				return KeyValues.of(kv);
			}

			String result = switch (target) {
				case KEY, DEFAULT -> command.execute(kv.key());
				case VALUE -> command.execute(kv.value());
			};

			if (result == null) {
				return KeyValues.empty();
			}

			return switch (target) {
				case KEY, DEFAULT -> {
					yield KeyValues.of(kv.withKey(result));
				}
				case VALUE -> {
					if (kv.value().equals(result)) {
						yield KeyValues.of(kv);
					}
					yield KeyValues.of(kv.withSealedValue(result));
				}
			};
		}

	},
	JOIN(KeyValuesResource.FILTER_JOIN) {
		@Override
		protected KeyValues doFilter(FilterContext context, KeyValues keyValues, String expression, Target target) {
			Objects.requireNonNull(keyValues);
			SequencedMap<String, KeyValue> m = new LinkedHashMap<>();
			for (var kv : keyValues) {
				var found = m.get(kv.key());
				if (found != null) {
					m.put(kv.key(), kv.withExpanded(found.expanded() + expression + kv.expanded()));
				}
				else {
					m.put(kv.key(), kv);
				}
			}
			return KeyValues.copyOf(m.values().stream().toList());
		}
	},

	;

	private final String filter;

	private DefaultKeyValuesFilter(String filter) {
		this.filter = filter;
	}

	@Override
	public int order() {
		return BUILTIN_ORDER_START + ordinal();
	}

	@Override
	public Optional<KeyValues> filter(FilterContext context, KeyValues keyValues, Filter filter) {
		String filterName = filter.filter();
		Target target;
		String resolvedFilterName;

		if (filterName.endsWith(KeyValuesResource.FILTER_TARGET_KEY)) {
			target = Target.KEY;
			resolvedFilterName = filterName.substring(0,
					filterName.length() - KeyValuesResource.FILTER_TARGET_KEY.length());
		}
		else if (filterName.endsWith(KeyValuesResource.FILTER_TARGET_VAL)) {
			target = Target.VALUE;
			resolvedFilterName = filterName.substring(0,
					filterName.length() - KeyValuesResource.FILTER_TARGET_VAL.length());
		}
		else if (filterName.endsWith(KeyValuesResource.FILTER_TARGET_VALUE)) {
			target = Target.VALUE;
			resolvedFilterName = filterName.substring(0,
					filterName.length() - KeyValuesResource.FILTER_TARGET_VALUE.length());
		}
		else {
			target = Target.DEFAULT;
			resolvedFilterName = filterName;
		}

		if (this.filter.equalsIgnoreCase(resolvedFilterName)) {
			return Optional.of(doFilter(context, keyValues, filter.expression(), target));
		}
		return Optional.empty();
	}

	protected abstract KeyValues doFilter(FilterContext context, KeyValues keyValues, String expression, Target target);

	enum Target {

		KEY, VALUE, DEFAULT;

	}

}
