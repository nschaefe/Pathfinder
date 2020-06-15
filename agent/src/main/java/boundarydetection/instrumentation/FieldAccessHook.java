package boundarydetection.instrumentation;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.convert.Transformer;

import java.util.function.Predicate;

public abstract class FieldAccessHook extends TransformerBase {

    protected String methodClassname;
    private Predicate<String> filter;

    public FieldAccessHook(Transformer next, String methodClassname, Predicate<String> filter) {
        super(next);
        this.filter = filter;
        this.methodClassname = methodClassname;
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

    /**
     * Returns the class in which the field with the given index is declared.
     * Refering to the declaring class is important if fields are accessed in sub classes or overloaded
     */
    protected String getFieldRefDeclaringClassName(CtClass cl, ConstPool cp, int index) {
        CtField f = null;
        try {
            f = cl.getClassPool().get(cp.getFieldrefClassName(index)).getField(cp.getFieldrefName(index), cp.getFieldrefType(index));
            return f.getDeclaringClass().getName();
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return "BUG: NO CLASSNAME";
    }

    protected int addLdc(int i, CodeIterator iterator, int pos) throws BadBytecode {
        if (i > 0xFF) {
            pos = iterator.insertGap(3);
            iterator.writeByte(LDC_W, pos);
            iterator.write16bit(i, pos + 1);
            pos += 3;
            CodeAttribute ca = iterator.get();
            ca.setMaxStack(ca.getMaxStack() + 2);

        } else {
            pos = iterator.insertGap(2);
            iterator.writeByte(LDC, pos);
            iterator.writeByte(i, pos + 1);
            pos += 2;
            CodeAttribute ca = iterator.get();
            ca.setMaxStack(ca.getMaxStack() + 1);
        }
        return pos;
    }


    protected boolean toInstrument(String typedesc) {
        return filter == null ? true : filter.test(typedesc);
    }
}
