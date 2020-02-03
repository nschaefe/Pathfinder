package boundarydetection.instrumentation;

/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */


import javassist.*;
import javassist.convert.TransformWriteField;

/**
 * Simple translator of method bodies
 * (also see the <code>javassist.expr</code> package).
 *
 * <p>Instances of this class specifies how to instrument of the
 * bytecodes representing a method body.  They are passed to
 * <code>CtClass.instrument()</code> or
 * <code>CtMethod.instrument()</code> as a parameter.
 *
 * <p>Example:
 * <pre>
 * ClassPool cp = ClassPool.getDefault();
 * CtClass point = cp.get("Point");
 * CtClass singleton = cp.get("Singleton");
 * CtClass client = cp.get("Client");
 * CodeConverter conv = new CodeConverter();
 * conv.replaceNew(point, singleton, "makePoint");
 * client.instrument(conv);
 * </pre>
 *
 * <p>This program substitutes "<code>Singleton.makePoint()</code>"
 * for all occurrences of "<code>new Point()</code>"
 * appearing in methods declared in a <code>Client</code> class.
 *
 * @see javassist.expr.ExprEditor
 */
public class CodeInstrumenter extends CodeConverter {
    /**
     * Modify a method body so that an expression reading the specified
     * field is replaced with a call to the specified <i>static</i> method.
     * This static method receives the target object of the original
     * read expression as a parameter.  It must return a value of
     * the same type as the field.
     *
     * <p>For example, the program below
     *
     * <pre>Point p = new Point();
     * int newX = p.x + 3;</pre>
     *
     * <p>can be translated into:
     *
     * <pre>Point p = new Point();
     * int newX = Accessor.readX(p) + 3;</pre>
     *
     * <p>where
     *
     * <pre>public class Accessor {
     *     public static int readX(Object target) { ... }
     * }</pre>
     *
     * <p>The type of the parameter of <code>readX()</code> must
     * be <code>java.lang.Object</code> independently of the actual
     * type of <code>target</code>.  The return type must be the same
     * as the field type.
     *
     * @param field        the field.
     * @param calledClass  the class in which the static method is
     *                     declared.
     * @param calledMethod the name of the static method.
     */
    public void replaceFieldRead(CtField field,
                                 CtClass calledClass, String calledMethod) {
        transformers = new FieldReadHook(transformers, field,
                calledClass.getName(),
                calledMethod);
    }

    /**
     * Modify a method body so that an expression writing the specified
     * field is replaced with a call to the specified static method.
     * This static method receives two parameters: the target object of
     * the original
     * write expression and the assigned value.  The return type of the
     * static method is <code>void</code>.
     *
     * <p>For example, the program below
     *
     * <pre>Point p = new Point();
     * p.x = 3;</pre>
     *
     * <p>can be translated into:
     *
     * <pre>Point p = new Point();
     * Accessor.writeX(3);</pre>
     *
     * <p>where
     *
     * <pre>public class Accessor {
     *     public static void writeX(Object target, int value) { ... }
     * }</pre>
     *
     * <p>The type of the first parameter of <code>writeX()</code> must
     * be <code>java.lang.Object</code> independently of the actual
     * type of <code>target</code>.  The type of the second parameter
     * is the same as the field type.
     *
     * @param field        the field.
     * @param calledClass  the class in which the static method is
     *                     declared.
     * @param calledMethod the name of the static method.
     */
    public void replaceFieldWrite(CtField field,
                                  CtClass calledClass, String calledMethod) {
        transformers = new TransformWriteField(transformers, field,
                calledClass.getName(),
                calledMethod);
    }


}