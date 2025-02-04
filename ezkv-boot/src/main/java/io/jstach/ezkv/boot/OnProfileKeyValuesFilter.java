package io.jstach.ezkv.boot;

import java.util.Optional;
import java.util.Set;

import io.jstach.ezkv.kvs.KeyValues;
import io.jstach.ezkv.kvs.KeyValuesException;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter;

class OnProfileKeyValuesFilter implements KeyValuesFilter {

	@Override
	public Optional<KeyValues> filter(FilterContext context, KeyValues keyValues, Filter filter)
			throws IllegalArgumentException, KeyValuesException {
		if (!filter.filter().equals("onprofile")) {
			return Optional.empty();
		}
		var map = keyValues.toMap();
		String activateOn = context.environment().qualifyMetaKey("config.activate.on-profile");
		String profileExp = map.get(activateOn);
		if (profileExp == null) {
			return Optional.of(keyValues);
		}
		// context.environment().getLogger().debug("Found profile exp: " + profileExp);
		var _profiles = Profiles.of(profileExp);
		var selectedProfiles = Set.copyOf(context.profiles());
		if (_profiles.matches(selectedProfiles::contains)) {
			return Optional.of(keyValues.filter(kv -> !kv.key().equals(activateOn)));
		}
		return Optional.of(KeyValues.empty());
	}

}
