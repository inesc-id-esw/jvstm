package jvstm;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
public @interface Atomic { 
    TxType value() default TxType.RO;
}
