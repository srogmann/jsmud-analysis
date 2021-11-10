package org.rogmann.jsmud.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Example class-loader to be used in JUnit-tests.
 * <p>It loads the classes ClassA and ClassB.</p>
 */
public class ExampleClassLoader extends ClassLoader {

	/** parent class-loader */
	private final ClassLoader fClParent;

	/** name of the class-loader */
	private final String fName;

	/** our package */
	private final String fPackage;

	/** map from resource-name to bytecode */
	private final Map<String, byte[]> fMapClasses = new HashMap<>();

	/** map of defined classes */
	private final Map<String, Class<?>> fMapDefinedClasses = new HashMap<>();

	/**
	 * Constructor
	 * @param name name of the class-loader
	 */
	public ExampleClassLoader(final String name, final ClassLoader clParent) {
		fClParent = clParent;
		fName = name;
		fPackage = getClass().getName().replaceFirst("[.][^.]*$", "");

		final String prefix = '/' + fPackage.replace('.', '/') + '/';
		fMapClasses.put(prefix + "ClassA.class", readDump(XXD_CLASS_A));
		fMapClasses.put(prefix + "ClassB.class", readDump(XXD_CLASS_B));
	}

	/** Hex-dump of ClassA.class */
	private static final String[] XXD_CLASS_A = {
		"00000000: cafe babe 0000 0034 003d 0700 0201 001d  .......4.=......",
		"00000010: 6f72 672f 726f 676d 616e 6e2f 6a73 6d75  org/rogmann/jsmu",
		"00000020: 642f 7465 7374 2f43 6c61 7373 4107 0004  d/test/ClassA...",
		"00000030: 0100 106a 6176 612f 6c61 6e67 2f4f 626a  ...java/lang/Obj",
		"00000040: 6563 7407 0006 0100 1b6a 6176 612f 7574  ect......java/ut",
		"00000050: 696c 2f66 756e 6374 696f 6e2f 5375 7070  il/function/Supp",
		"00000060: 6c69 6572 0100 063c 696e 6974 3e01 0003  lier...<init>...",
		"00000070: 2829 5601 0004 436f 6465 0a00 0300 0b0c  ()V...Code......",
		"00000080: 0007 0008 0100 0f4c 696e 654e 756d 6265  .......LineNumbe",
		"00000090: 7254 6162 6c65 0100 124c 6f63 616c 5661  rTable...LocalVa",
		"000000a0: 7269 6162 6c65 5461 626c 6501 0004 7468  riableTable...th",
		"000000b0: 6973 0100 1f4c 6f72 672f 726f 676d 616e  is...Lorg/rogman",
		"000000c0: 6e2f 6a73 6d75 642f 7465 7374 2f43 6c61  n/jsmud/test/Cla",
		"000000d0: 7373 413b 0100 0367 6574 0100 1428 294c  ssA;...get...()L",
		"000000e0: 6a61 7661 2f6c 616e 672f 5374 7269 6e67  java/lang/String",
		"000000f0: 3b12 0000 0013 0c00 1000 1401 001f 2829  ;.............()",
		"00000100: 4c6a 6176 612f 7574 696c 2f66 756e 6374  Ljava/util/funct",
		"00000110: 696f 6e2f 5375 7070 6c69 6572 3b0b 0005  ion/Supplier;...",
		"00000120: 0016 0c00 1000 1701 0014 2829 4c6a 6176  ..........()Ljav",
		"00000130: 612f 6c61 6e67 2f4f 626a 6563 743b 0700  a/lang/Object;..",
		"00000140: 1901 0010 6a61 7661 2f6c 616e 672f 5374  ....java/lang/St",
		"00000150: 7269 6e67 0100 0873 7570 706c 6965 7201  ring...supplier.",
		"00000160: 001d 4c6a 6176 612f 7574 696c 2f66 756e  ..Ljava/util/fun",
		"00000170: 6374 696f 6e2f 5375 7070 6c69 6572 3b01  ction/Supplier;.",
		"00000180: 0016 4c6f 6361 6c56 6172 6961 626c 6554  ..LocalVariableT",
		"00000190: 7970 6554 6162 6c65 0100 314c 6a61 7661  ypeTable..1Ljava",
		"000001a0: 2f75 7469 6c2f 6675 6e63 7469 6f6e 2f53  /util/function/S",
		"000001b0: 7570 706c 6965 723c 4c6a 6176 612f 6c61  upplier<Ljava/la",
		"000001c0: 6e67 2f53 7472 696e 673b 3e3b 0a00 0100  ng/String;>;....",
		"000001d0: 1f0c 0010 0011 0100 086c 616d 6264 6124  .........lambda$",
		"000001e0: 300a 0022 0024 0700 2301 001d 6f72 672f  0..\".$..#...org/",
		"000001f0: 726f 676d 616e 6e2f 6a73 6d75 642f 7465  rogmann/jsmud/te",
		"00000200: 7374 2f43 6c61 7373 420c 0025 0011 0100  st/ClassB..%....",
		"00000210: 0767 6574 4e61 6d65 0100 0a53 6f75 7263  .getName...Sourc",
		"00000220: 6546 696c 6501 000b 436c 6173 7341 2e6a  eFile...ClassA.j",
		"00000230: 6176 6101 0009 5369 676e 6174 7572 6501  ava...Signature.",
		"00000240: 0043 4c6a 6176 612f 6c61 6e67 2f4f 626a  .CLjava/lang/Obj",
		"00000250: 6563 743b 4c6a 6176 612f 7574 696c 2f66  ect;Ljava/util/f",
		"00000260: 756e 6374 696f 6e2f 5375 7070 6c69 6572  unction/Supplier",
		"00000270: 3c4c 6a61 7661 2f6c 616e 672f 5374 7269  <Ljava/lang/Stri",
		"00000280: 6e67 3b3e 3b01 0010 426f 6f74 7374 7261  ng;>;...Bootstra",
		"00000290: 704d 6574 686f 6473 0a00 2c00 2e07 002d  pMethods..,....-",
		"000002a0: 0100 226a 6176 612f 6c61 6e67 2f69 6e76  ..\"java/lang/inv",
		"000002b0: 6f6b 652f 4c61 6d62 6461 4d65 7461 6661  oke/LambdaMetafa",
		"000002c0: 6374 6f72 790c 002f 0030 0100 0b6d 6574  ctory../.0...met",
		"000002d0: 6166 6163 746f 7279 0100 cc28 4c6a 6176  afactory...(Ljav",
		"000002e0: 612f 6c61 6e67 2f69 6e76 6f6b 652f 4d65  a/lang/invoke/Me",
		"000002f0: 7468 6f64 4861 6e64 6c65 7324 4c6f 6f6b  thodHandles$Look",
		"00000300: 7570 3b4c 6a61 7661 2f6c 616e 672f 5374  up;Ljava/lang/St",
		"00000310: 7269 6e67 3b4c 6a61 7661 2f6c 616e 672f  ring;Ljava/lang/",
		"00000320: 696e 766f 6b65 2f4d 6574 686f 6454 7970  invoke/MethodTyp",
		"00000330: 653b 4c6a 6176 612f 6c61 6e67 2f69 6e76  e;Ljava/lang/inv",
		"00000340: 6f6b 652f 4d65 7468 6f64 5479 7065 3b4c  oke/MethodType;L",
		"00000350: 6a61 7661 2f6c 616e 672f 696e 766f 6b65  java/lang/invoke",
		"00000360: 2f4d 6574 686f 6448 616e 646c 653b 4c6a  /MethodHandle;Lj",
		"00000370: 6176 612f 6c61 6e67 2f69 6e76 6f6b 652f  ava/lang/invoke/",
		"00000380: 4d65 7468 6f64 5479 7065 3b29 4c6a 6176  MethodType;)Ljav",
		"00000390: 612f 6c61 6e67 2f69 6e76 6f6b 652f 4361  a/lang/invoke/Ca",
		"000003a0: 6c6c 5369 7465 3b0f 0600 2b10 0017 0a00  llSite;...+.....",
		"000003b0: 0100 340c 0020 0011 0f06 0033 1000 1101  ..4.. .....3....",
		"000003c0: 000c 496e 6e65 7243 6c61 7373 6573 0700  ..InnerClasses..",
		"000003d0: 3901 0025 6a61 7661 2f6c 616e 672f 696e  9..%java/lang/in",
		"000003e0: 766f 6b65 2f4d 6574 686f 6448 616e 646c  voke/MethodHandl",
		"000003f0: 6573 244c 6f6f 6b75 7007 003b 0100 1e6a  es$Lookup..;...j",
		"00000400: 6176 612f 6c61 6e67 2f69 6e76 6f6b 652f  ava/lang/invoke/",
		"00000410: 4d65 7468 6f64 4861 6e64 6c65 7301 0006  MethodHandles...",
		"00000420: 4c6f 6f6b 7570 0021 0001 0003 0001 0005  Lookup.!........",
		"00000430: 0000 0004 0001 0007 0008 0001 0009 0000  ................",
		"00000440: 002f 0001 0001 0000 0005 2ab7 000a b100  ./........*.....",
		"00000450: 0000 0200 0c00 0000 0600 0100 0000 0900  ................",
		"00000460: 0d00 0000 0c00 0100 0000 0500 0e00 0f00  ................",
		"00000470: 0000 0100 1000 1100 0100 0900 0000 5a00  ..............Z.",
		"00000480: 0100 0200 0000 10ba 0012 0000 4c2b b900  ............L+..",
		"00000490: 1501 00c0 0018 b000 0000 0300 0c00 0000  ................",
		"000004a0: 0a00 0200 0000 0d00 0600 0e00 0d00 0000  ................",
		"000004b0: 1600 0200 0000 1000 0e00 0f00 0000 0600  ................",
		"000004c0: 0a00 1a00 1b00 0100 1c00 0000 0c00 0100  ................",
		"000004d0: 0600 0a00 1a00 1d00 0110 4100 1000 1700  ..........A.....",
		"000004e0: 0100 0900 0000 2500 0100 0100 0000 052a  ......%........*",
		"000004f0: b600 1eb0 0000 0002 000c 0000 0006 0001  ................",
		"00000500: 0000 0001 000d 0000 0002 0000 100a 0020  ............... ",
		"00000510: 0011 0001 0009 0000 0024 0001 0000 0000  .........$......",
		"00000520: 0004 b800 21b0 0000 0002 000c 0000 0006  ....!...........",
		"00000530: 0001 0000 000d 000d 0000 0002 0000 0004  ................",
		"00000540: 0026 0000 0002 0027 0028 0000 0002 0029  .&.....'.(.....)",
		"00000550: 002a 0000 000c 0001 0031 0003 0032 0035  .*.......1...2.5",
		"00000560: 0036 0037 0000 000a 0001 0038 003a 003c  .6.7.......8.:.<",
		"00000570: 0019                                     .."
	};

	/** Hex-dump of ClassB.class */
	private static final String[] XXD_CLASS_B = {
		"00000000: cafe babe 0000 0034 0017 0700 0201 001d  .......4........",
		"00000010: 6f72 672f 726f 676d 616e 6e2f 6a73 6d75  org/rogmann/jsmu",
		"00000020: 642f 7465 7374 2f43 6c61 7373 4207 0004  d/test/ClassB...",
		"00000030: 0100 106a 6176 612f 6c61 6e67 2f4f 626a  ...java/lang/Obj",
		"00000040: 6563 7401 0006 3c69 6e69 743e 0100 0328  ect...<init>...(",
		"00000050: 2956 0100 0443 6f64 650a 0003 0009 0c00  )V...Code.......",
		"00000060: 0500 0601 000f 4c69 6e65 4e75 6d62 6572  ......LineNumber",
		"00000070: 5461 626c 6501 0012 4c6f 6361 6c56 6172  Table...LocalVar",
		"00000080: 6961 626c 6554 6162 6c65 0100 0474 6869  iableTable...thi",
		"00000090: 7301 001f 4c6f 7267 2f72 6f67 6d61 6e6e  s...Lorg/rogmann",
		"000000a0: 2f6a 736d 7564 2f74 6573 742f 436c 6173  /jsmud/test/Clas",
		"000000b0: 7342 3b01 0007 6765 744e 616d 6501 0014  sB;...getName...",
		"000000c0: 2829 4c6a 6176 612f 6c61 6e67 2f53 7472  ()Ljava/lang/Str",
		"000000d0: 696e 673b 0a00 1100 1307 0012 0100 0f6a  ing;...........j",
		"000000e0: 6176 612f 6c61 6e67 2f43 6c61 7373 0c00  ava/lang/Class..",
		"000000f0: 1400 0f01 000d 6765 7453 696d 706c 654e  ......getSimpleN",
		"00000100: 616d 6501 000a 536f 7572 6365 4669 6c65  ame...SourceFile",
		"00000110: 0100 0b43 6c61 7373 422e 6a61 7661 0021  ...ClassB.java.!",
		"00000120: 0001 0003 0000 0000 0002 0001 0005 0006  ................",
		"00000130: 0001 0007 0000 002f 0001 0001 0000 0005  ......./........",
		"00000140: 2ab7 0008 b100 0000 0200 0a00 0000 0600  *...............",
		"00000150: 0100 0000 0300 0b00 0000 0c00 0100 0000  ................",
		"00000160: 0500 0c00 0d00 0000 0900 0e00 0f00 0100  ................",
		"00000170: 0700 0000 2600 0100 0000 0000 0612 01b6  ....&...........",
		"00000180: 0010 b000 0000 0200 0a00 0000 0600 0100  ................",
		"00000190: 0000 0600 0b00 0000 0200 0000 0100 1500  ................",
		"000001a0: 0000 0200 16                             ....."
	};

	/** {@inheritDoc} */
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		Class<?> clazz = fMapDefinedClasses.get(name);
		if (clazz == null) {
			final byte[] buf = fMapClasses.get('/' + name.replace('.', '/') + ".class");
			if (buf != null) {
				clazz = super.defineClass(name, buf, 0, buf.length);
				fMapDefinedClasses.put(name, clazz);
			}
			else {
				clazz = fClParent.loadClass(name);
			}
		}
		return clazz;
	}

	/** {@inheritDoc} */
    @Override
	public InputStream getResourceAsStream(String name) {
    	InputStream is = null;
    	byte[] buf = fMapClasses.get(name);
    	if (buf == null && name.length() > 0 && name.charAt(0) != '/') {
    		final String absName = '/' + name;
    		buf = fMapClasses.get(absName);
    	}
    	if (buf != null) {
    		is = new ByteArrayInputStream(buf);
    	}
    	return is;
    }

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return fName;
	}

	/**
	 * Simple hex-dump-parser.
	 * @param lines hex-dump (generated by xxd)
	 * @return buffer
	 */
	static byte[] readDump(final String[] lines) {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream(lines.length * 16);
		for (String line : lines) {
			final String hexDigits = line.substring(10, 50).replace(" ", "");
			for (int i = 0; i < hexDigits.length(); i += 2) {
				baos.write(Integer.parseInt(hexDigits.substring(i, i + 2), 16));
			}
		}
		return baos.toByteArray();
	}
}
