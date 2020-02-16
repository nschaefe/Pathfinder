package boundarydetection.instrumentation;

import javassist.*;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.convert.Transformer;

public abstract class FieldAccessHook extends Transformer {

    protected MethodInfo methodInfo; //TODO not initialized until init
    protected String className;
    protected String methodClassname;
    protected String methodName;

    public FieldAccessHook(Transformer next, String methodClassname, String methodName) {
        super(next);
        this.methodClassname = methodClassname;
        this.methodName = methodName;
    }

    @Override
    public void initialize(ConstPool cp, CtClass clazz, MethodInfo minfo) throws CannotCompileException {
        methodInfo = minfo;
        //methodInfo.doPreverify=true;
        this.className = clazz.getName();
        initialize(cp, minfo.getCodeAttribute());
    }


    protected static String isField(ClassPool pool, ConstPool cp, CtClass fclass,
                                    String fname, boolean is_private, int index) {
        if (!cp.getFieldrefName(index).equals(fname))
            return null;

        try {
            CtClass c = pool.get(cp.getFieldrefClassName(index));
            if (c == fclass || (!is_private && isFieldInSuper(c, fclass, fname)))
                return cp.getFieldrefType(index);
        } catch (NotFoundException e) {
        }
        return null;
    }

    protected static boolean isFieldInSuper(CtClass clazz, CtClass fclass, String fname) {
        if (!clazz.subclassOf(fclass))
            return false;

        try {
            CtField f = clazz.getField(fname);
            return f.getDeclaringClass() == fclass;
        } catch (NotFoundException e) {
        }
        return false;
    }

    protected int addLdc(int i, CodeIterator iterator, int pos) throws BadBytecode {
        if (i > 0xFF) {
            pos = iterator.insertGap(3);
            iterator.writeByte(LDC_W, pos);
            iterator.write16bit(i, pos + 1);
            pos += 3;

        } else {
            pos = iterator.insertGap(2);
            iterator.writeByte(LDC, pos);
            iterator.writeByte(i, pos + 1);
            pos += 2;
        }
        return pos;
    }

}
