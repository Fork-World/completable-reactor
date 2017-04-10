package ru.fix.completable.reactor.runtime.internal;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Kamil Asfandiyarov
 */
@Slf4j
public class LambdaReflector {

    @Value
    public static class AnnotatedMethod<AnnotationType> {
        Class methodClass;
        Method method;
        Optional<AnnotationType> annotation;
    }

    public static <AnnotationType extends Annotation> AnnotatedMethod<AnnotationType> annotatedMethodReference(
            Serializable methodReference,
            Class<AnnotationType> annotationClass) {

        return methodReference(methodReference)
                .map(reference -> new AnnotatedMethod<>(
                        reference.getMethodClass(),
                        reference.getMethod(),
                        Optional.ofNullable(reference.getMethod().getAnnotation(annotationClass))))

                .orElseThrow(() -> new IllegalArgumentException("Failed to reflect method reference."));
    }

    @Value
    public static class MethodReference {
        Class methodClass;
        Method method;
    }

    static Optional<MethodReference> methodReference(Serializable methodReference) {

        SerializedLambda serializedLambda = null;

        for (Class<?> clazz = methodReference.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Method serializationMethod = clazz.getDeclaredMethod("writeReplace");
                serializationMethod.setAccessible(true);
                Object serializedObject = serializationMethod.invoke(methodReference);
                if (!(serializedObject instanceof SerializedLambda)) {
                    break;
                }

                serializedLambda = (SerializedLambda) serializedObject;
                break;
            } catch (NoSuchMethodException noSuchMethodExcetpion) {
                log.trace("Ignore method not found.", noSuchMethodExcetpion);
            } catch (Exception exc) {
                log.warn("Failed to reflect lambda object:" + methodReference, methodReference, exc);
                break;
            }
        }

        if (serializedLambda == null || serializedLambda.getImplClass() == null || serializedLambda.getImplMethodName() == null) {
            return Optional.empty();
        }

        String className = serializedLambda.getImplClass().replace('/', '.');
        String methodName = serializedLambda.getImplMethodName();
        String methodSignature = serializedLambda.getImplMethodSignature();

        try {
            Class methodClass = Class.forName(className);

            List<Method> methods = Arrays.stream(methodClass.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .collect(Collectors.toList());

            if (methods.size() == 0) {
                log.warn("Can not find method {} in class {}", methodName, className);
                return Optional.empty();
            }

            if (methods.size() == 1) {
                return Optional.of(new MethodReference(methodClass, methods.get(0)));
            }

            List<Method> methodsWithMatchedSignatures = methods.stream()
                    .filter(method -> compareSignatures(method, methodSignature))
                    .collect(Collectors.toList());

            if (methodsWithMatchedSignatures.size() == 0) {
                return Optional.empty();
            } else if (methodsWithMatchedSignatures.size() == 1) {
                return Optional.of(new MethodReference(methodClass, methodsWithMatchedSignatures.get(0)));
            } else {
                throw new UnsupportedOperationException("To many methods matched same signature: " + methodSignature);
            }

        } catch (Exception exc) {
            log.warn("Failed to reflect method {} of class {} with signature {}", methodName, className, methodSignature, exc);
            return Optional.empty();
        }
    }


    static String signatureMapType(Class type) {
        if (type.isArray()) {
            //hope stack size will be enough
            return "[" + signatureMapType(type.getComponentType());
        }
        if (type.equals(Boolean.TYPE)) {
            return "Z";
        }
        if (type.equals(Byte.TYPE)) {
            return "B";
        }
        if (type.equals(Character.TYPE)) {
            return "C";
        }
        if (type.equals(Double.TYPE)) {
            return "D";
        }
        if (type.equals(Float.TYPE)) {
            return "F";
        }
        if (type.equals(Integer.TYPE)) {
            return "I";
        }
        if (type.equals(Long.TYPE)) {
            return "J";
        }
        if (type.equals(Short.TYPE)) {
            return "S";
        }
        if (type.equals(Void.TYPE)) {
            return "V";
        }
        //Object
        return "L" + type.getName().replace('.', '/') + ";";
    }

    static String signature(Method method) {
        StringBuilder signature = new StringBuilder();

        Parameter[] parameters = method.getParameters();
        signature.append("(");
        for (Parameter parameter : parameters) {
            signature.append(signatureMapType(parameter.getType()));
        }
        signature.append(")");

        signature.append(signatureMapType(method.getReturnType()));

        return signature.toString();
    }

    static boolean compareSignatures(Method method, String signature) {
        return signature(method).equals(signature);
    }
}
