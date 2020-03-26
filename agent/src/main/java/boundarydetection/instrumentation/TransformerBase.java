package boundarydetection.instrumentation;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.convert.Transformer;

public abstract class TransformerBase extends Transformer {

    protected MethodInfo methodInfo; //TODO not initialized until init
    protected String className;

    public TransformerBase(Transformer next) {
        super(next);
    }

    @Override
    public void initialize(ConstPool cp, CtClass clazz, MethodInfo minfo) throws CannotCompileException {
        methodInfo = minfo;
        //methodInfo.doPreverify=true;
        this.className = clazz.getName();
        initialize(cp, minfo.getCodeAttribute());
    }

}
