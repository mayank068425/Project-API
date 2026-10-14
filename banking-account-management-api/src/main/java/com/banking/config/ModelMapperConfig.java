package com.banking.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ModelMapper, configured to be as unsurprising as possible.
 *
 * <h2>Why STRICT, and why the hand-written mappers still exist</h2>
 * The default matching strategy is STANDARD, which matches source and
 * destination properties by loose token similarity. On a banking model that is
 * genuinely dangerous: {@code Account.balance} and a response field called
 * {@code availableBalance} are similar enough for the default strategy to
 * consider a match, and the two are not the same number. STRICT requires
 * property names to correspond exactly.
 *
 * <p>Ambiguity is made fatal rather than resolved by a coin flip, and null
 * sources are skipped so a partial update request does not blank out fields it
 * never mentioned.
 *
 * <p>Even so, the mappers in {@code com.banking.mapper} are written by hand. A
 * reflective mapper copies every field whose name matches — including
 * {@code Customer.nationalId}, which must never leave the system. An omission
 * enforced by a hand-written method is visible; an omission that depends on a
 * response record happening not to declare a field is not, and it silently
 * reverses the moment somebody adds one.
 *
 * <p>This bean is here for the straightforward shapes and for code that
 * legitimately wants it, not as the default path for anything carrying money or
 * identity.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setAmbiguityIgnored(false)
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setPreferNestedProperties(false);
        return mapper;
    }
}
