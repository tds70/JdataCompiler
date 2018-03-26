import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureClassLoader;
import java.util.*;

public class JdataCompiler {

    private JavaCompiler compiler ; // == SystemJavaCompiler
    private JavaFileManager fileManager ; // == our ClassFileManager
    // all the source codes ( compiled or not yet )
    // a class can be compiled only once for a compiler ( but more than one compiler can be used )
    private HashMap<String,String> sourceCodes = new HashMap<>();
    // all code still to be compiled
    private HashSet<String> classToCompile = new HashSet<>();

    public JdataCompiler() {
        compiler = ToolProvider.getSystemJavaCompiler();
        fileManager = new
                ClassFileManager(compiler
                .getStandardFileManager(null, null, null));
    }

    public boolean addClassCode( String className , String sourceCode){
        if ( sourceCodes.containsKey(className)) return  false ;
        sourceCodes.put(className,sourceCode);
        classToCompile.add(className);
        return  true;
    }

    public void compile() throws Exception {
        if ( classToCompile.isEmpty() ) {
            return ;
        }
        List<JavaFileObject> SourceCodes = new ArrayList<>();
        for ( String className : classToCompile){
            SourceCodes.add(new StringSourceCode(className,sourceCodes.get(className)));
        }
        boolean statusOk = compiler.getTask(null, fileManager, null, null,
                null, SourceCodes).call();
        if ( statusOk ) {
            classToCompile.clear();
        } else {
            // remove class not compiled because faulty
            for ( String name : classToCompile){
                sourceCodes.remove(name);
            }
            classToCompile.clear();
            throw new Exception("Compilation error");
        }
    }

    public Class<?> getClass(String name) throws Exception {
        // getting a class trigger the compilation of the pending ones
        if ( ! classToCompile.isEmpty()){
            compile();
        }
        return fileManager.getClassLoader(null).loadClass(name);
    }
    //========================================================================================================
    /**
     * Store all compiled bytecode of the different compilation
     * or the different inner classes or anonymous classes if any
     */
    private static class ClassFileManager extends ForwardingJavaFileManager {
        private HashMap<String, JavaByteCodeBuffer> byteCodes = new HashMap<>();
        /**
         * Will initialize the manager with the specified standard java file manager
         */
        public ClassFileManager(StandardJavaFileManager
                                        standardManager) {
            super(standardManager);
        }
        /**
         * Class loader retrieving the class from the byte code following the className
         */
        @Override
        public ClassLoader getClassLoader(JavaFileManager.Location location) {
            return new SecureClassLoader() {
                @Override
                protected Class<?> findClass(String className)
                        throws ClassNotFoundException {
                    JavaByteCodeBuffer byteCode = ClassFileManager.this.byteCodes.get(className);
                    if ( byteCode == null) throw new ClassNotFoundException("Class not found :"+className);
                    byte[] byteBuffer = byteCode.getBytes();
                    return super.defineClass(className, byteBuffer, 0, byteBuffer.length);
                }
            };
        }
        /**
         * For any classes compiled by the compiler
         * give a virtual file where to store the byte code
         * ( more than one call can arise , because of inner and anonymous classes )
         */
        public JavaByteCodeBuffer getJavaFileForOutput(JavaFileManager.Location location
                , String className
                , JavaFileObject.Kind kind
                , FileObject sibling) throws IOException {
            JavaByteCodeBuffer javaByteCodeBuffer ;
            javaByteCodeBuffer = new JavaByteCodeBuffer(className, kind);
            byteCodes.put(className,javaByteCodeBuffer);
            return javaByteCodeBuffer;
        }
    }
    //========================================================================================================
    /**
     * Vitual file in memory to store the byte codes of the compiler
     */
    private static class JavaByteCodeBuffer extends SimpleJavaFileObject {
        private ByteArrayOutputStream byteArrayOutputStream ;
        /**
         * Register as a virtual file name ( from the binary class name )
         */
        JavaByteCodeBuffer(String className, Kind kind) {
            super(URI.create("string:///"
                    + className.replace('.', '/')
                    + kind.extension), kind);
            byteArrayOutputStream = new ByteArrayOutputStream();
        }
        /**
         * openOutputStream give a output stream to write the file
         */
        @Override
        public OutputStream openOutputStream() throws IOException {
            return byteArrayOutputStream;
        }
        /**
         * Give the byte code back as a array of bytes
         */
        byte[] getBytes() {
            return byteArrayOutputStream.toByteArray();
        }
    }
    //========================================================================================================
    /**
     * Vitual file in memory to store the source codes for the compiler
     */
    public class StringSourceCode extends SimpleJavaFileObject {
        private String sourceCode;
        /**
         * Register as a virtual file name ( from the binary class name )
         */
        StringSourceCode(String className, String sourceCode) {
            super(URI.create("string:///"
                    + className.replace('.', '/')
                    + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }
        /**
         * Give the source code back (to the compiler) as a CharSequence
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }
}
