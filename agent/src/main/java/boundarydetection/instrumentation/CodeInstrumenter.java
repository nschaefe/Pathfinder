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


import javassist.CodeConverter;
import javassist.CtClass;

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

    public void replaceFieldRead(CtClass calledClass) {
        transformers = new ArrayFieldValueReadHook(transformers,
                calledClass.getName());
        transformers = new FieldReadHook(transformers,
                calledClass.getName());
    }


    public void replaceFieldWrite(
            CtClass calledClass) {
        transformers = new FieldWriteHook(transformers,
                calledClass.getName());
    }

    public void reformatConstructor() {
        transformers = new ConstructorReformater(transformers);
    }

}