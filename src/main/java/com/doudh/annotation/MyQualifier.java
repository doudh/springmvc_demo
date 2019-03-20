package com.doudh.annotation;

import java.lang.annotation.*;

@Target(value={ElementType.ANNOTATION_TYPE,ElementType.FIELD,
        ElementType.METHOD,ElementType.PARAMETER,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyQualifier {

    String value() default"";
}
