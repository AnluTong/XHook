package javassist.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.android.dex.util.FileUtils;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;

public class DexFile {
    private final com.android.dx.dex.file.DexFile file;
    private final DexOptions dex_options = new DexOptions();

    public DexFile() {
        this.file = new com.android.dx.dex.file.DexFile(dex_options);
    }

    public void addClass(String path, String pkgName, String className) {
        String pkg = pkgName.replaceAll("\\.", "/");
        File dfclass = new File(path, pkg + File.separator + className + ".class");

        final CfOptions cf_options = new CfOptions();
        byte[] bytecode = FileUtils.readFile(dfclass);
        DirectClassFile classfile = new DirectClassFile(bytecode, pkg + File.separator + className + ".class", cf_options.strictNameCheck);
        classfile.setAttributeFactory(StdAttributeFactory.THE_ONE);
        final ClassDefItem cdi = CfTranslator.translate(classfile, bytecode, cf_options, dex_options, file);
        file.add(cdi);
    }

    public void writeFile(String filePath) throws IOException {
        final FileOutputStream fos = new FileOutputStream(filePath);
        Throwable error = null;
        try {
            file.writeTo(fos, null, false);
        } catch (IOException e) {
            error = e;
        } finally {
            fos.close();
        }
        if (null != error) {
            new File(filePath).delete();
        }
    }
}
