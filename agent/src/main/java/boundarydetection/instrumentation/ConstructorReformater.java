package boundarydetection.instrumentation;

import javassist.CtClass;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import javassist.convert.Transformer;

public class ConstructorReformater extends TransformerBase {


    private boolean skippedConstCall = false;

    public ConstructorReformater(Transformer next) {
        super(next);
    }

    @Override
    public void clean() {
        skippedConstCall = false;
    }

    private boolean isConstructorCallNoPara(String clName, String mname, String type) {
        return  mname.equals("<init>") && type.equals("()V");
    }

    /*
     * Moves constructor calls that have no parameters to the top of the method
     *
     * In java constructor calls must be the first statement in a constructor
     * However the java compiler sometimes moves the constructor call to the end of the method and does field assignment
     * beforhand, so reorders. (e.g. super(42); this.i=i; -> putfield i; invokespecial superclass.init)
     * This reorder happens for example for an adhoc runnable implementation.
     *
     * We cannot pass "this" to a call until the base constructor has bee called (-> uninitialized this)
     * So Track.fieldWrite(this,..) at the point of the write would not be possible.
     * So we try to undo this reordering such that we can pass "this".
     *
     * But it is allowed to do almost everything in the parameter section of the call. (e.g. super(callable.call())
     * It is allowed to call methods that return a value that is passed to the constructor, if conditionals, parameters.
     * This makes it non-trivial to undo the reorder.
     * So we only reorder calls that have no parameters. At the time the developer wrote the code he must anyway place the call
     * as the first statement. So there cannot be unintended side effects.
     */
    private boolean moveConstructorCall(CodeIterator it, int index, ConstPool cp) throws BadBytecode {
        //code.addAload(0);
        //code.addInvokespecial("java/lang/Object", MethodInfo.nameInit, "()V");
        if (it.byteAt(index - 1) == Opcode.ALOAD_0 &&
                it.byteAt(index) == Opcode.INVOKESPECIAL) {

            int methodInfoIndex = it.byteAt(index + 1);
            methodInfoIndex = methodInfoIndex << 8;
            methodInfoIndex = methodInfoIndex | it.byteAt(index + 2);
            if (isConstructorCallNoPara(cp.getMethodrefClassName(methodInfoIndex),
                    cp.getMethodrefName(methodInfoIndex),
                    cp.getMethodrefType(methodInfoIndex))) {


                int i1 = it.byteAt(index - 1);
                int i2 = it.byteAt(index);
                int i3 = it.byteAt(index + 1);
                int i4 = it.byteAt(index + 2);

                it.writeByte(Opcode.NOP, index - 1);
                it.writeByte(Opcode.NOP, index);
                it.writeByte(Opcode.NOP, index + 1);
                it.writeByte(Opcode.NOP, index + 2);

                it.begin();
                int pos = it.insertGap(4);
                it.writeByte(i1, pos);
                it.writeByte(i2, pos + 1);
                it.writeByte(i3, pos + 2);
                it.writeByte(i4, pos + 3);

                it.move(index + 4);
                return true;

            }
        }
        return false;

    }

    @Override
    public int transform(CtClass tclazz, int pos, CodeIterator iterator,
                         ConstPool cp) throws BadBytecode {
        // jump over all instructions before super or this calls.
        // field accesses can happen before super in compiled code (java moves these instructions),
        // so not doing this can lead to a method call injection before super or this,
        // what leads to passing uninitializedThis to method call, what is not allowed
        if (methodInfo.isConstructor() && !skippedConstCall) {
            int onCallIndex = iterator.skipConstructor();
            if (onCallIndex != -1) {
                // jumping over super or this call, pos is after
                // we move super calls to java.lang.Object to the beginning of the constructor
                // to support more constructors. We can do this for that case because this constructor call
                // has no side effects. Doing this in general is not possible this way and not trivial.
                if (this.moveConstructorCall(iterator, onCallIndex, cp)) {
                    iterator.begin();
                    pos = 0;
                } else {
                    iterator.move(onCallIndex);
                    pos = iterator.next();
                }
            }
            skippedConstCall = true;
        }
        return pos;
    }
}
