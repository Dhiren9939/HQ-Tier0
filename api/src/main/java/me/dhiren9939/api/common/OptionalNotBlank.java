package me.dhiren9939.api.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Like @NotBlank, but null is valid too - for patch-style fields where null means "leave unchanged". */
@Documented
@Constraint(validatedBy = OptionalNotBlankValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalNotBlank {
    String message() default "must not be blank";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
