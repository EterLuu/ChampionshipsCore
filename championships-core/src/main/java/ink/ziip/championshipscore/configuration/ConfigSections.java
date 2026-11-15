package ink.ziip.championshipscore.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Static field owners loaded, validated and saved as part of the declaring configuration file. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigSections {
    Class<?>[] value();
}
