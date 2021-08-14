package org.rogmann.jsmud.vm;

/**
 * Helper-class for displaying names of opcodes.
 */
public class OpcodeDisplay {
	/** List of opcode-names */
	private static final String[] NAMES = new String[256];
	
	static {
		NAMES[0] = "NOP"; // visitInsn
		NAMES[1] = "ACONST_NULL"; // -
		NAMES[2] = "ICONST_M1"; // -
		NAMES[3] = "ICONST_0"; // -
		NAMES[4] = "ICONST_1"; // -
		NAMES[5] = "ICONST_2"; // -
		NAMES[6] = "ICONST_3"; // -
		NAMES[7] = "ICONST_4"; // -
		NAMES[8] = "ICONST_5"; // -
		NAMES[9] = "LCONST_0"; // -
		NAMES[10] = "LCONST_1"; // -
		NAMES[11] = "FCONST_0"; // -
		NAMES[12] = "FCONST_1"; // -
		NAMES[13] = "FCONST_2"; // -
		NAMES[14] = "DCONST_0"; // -
		NAMES[15] = "DCONST_1"; // -
		NAMES[16] = "BIPUSH"; // visitIntInsn
		NAMES[17] = "SIPUSH"; // -
		NAMES[18] = "LDC"; // visitLdcInsn
		NAMES[19] = "LDC_W";
		NAMES[20] = "LDC2_W";
		NAMES[21] = "ILOAD"; // visitVarInsn
		NAMES[22] = "LLOAD"; // -
		NAMES[23] = "FLOAD"; // -
		NAMES[24] = "DLOAD"; // -
		NAMES[25] = "ALOAD"; // -
		NAMES[46] = "IALOAD"; // visitInsn
		NAMES[47] = "LALOAD"; // -
		NAMES[48] = "FALOAD"; // -
		NAMES[49] = "DALOAD"; // -
		NAMES[50] = "AALOAD"; // -
		NAMES[51] = "BALOAD"; // -
		NAMES[52] = "CALOAD"; // -
		NAMES[53] = "SALOAD"; // -
		NAMES[54] = "ISTORE"; // visitVarInsn
		NAMES[55] = "LSTORE"; // -
		NAMES[56] = "FSTORE"; // -
		NAMES[57] = "DSTORE"; // -
		NAMES[58] = "ASTORE"; // -
		NAMES[79] = "IASTORE"; // visitInsn
		NAMES[80] = "LASTORE"; // -
		NAMES[81] = "FASTORE"; // -
		NAMES[82] = "DASTORE"; // -
		NAMES[83] = "AASTORE"; // -
		NAMES[84] = "BASTORE"; // -
		NAMES[85] = "CASTORE"; // -
		NAMES[86] = "SASTORE"; // -
		NAMES[87] = "POP"; // -
		NAMES[88] = "POP2"; // -
		NAMES[89] = "DUP"; // -
		NAMES[90] = "DUP_X1"; // -
		NAMES[91] = "DUP_X2"; // -
		NAMES[92] = "DUP2"; // -
		NAMES[93] = "DUP2_X1"; // -
		NAMES[94] = "DUP2_X2"; // -
		NAMES[95] = "SWAP"; // -
		NAMES[96] = "IADD"; // -
		NAMES[97] = "LADD"; // -
		NAMES[98] = "FADD"; // -
		NAMES[99] = "DADD"; // -
		NAMES[100] = "ISUB"; // -
		NAMES[101] = "LSUB"; // -
		NAMES[102] = "FSUB"; // -
		NAMES[103] = "DSUB"; // -
		NAMES[104] = "IMUL"; // -
		NAMES[105] = "LMUL"; // -
		NAMES[106] = "FMUL"; // -
		NAMES[107] = "DMUL"; // -
		NAMES[108] = "IDIV"; // -
		NAMES[109] = "LDIV"; // -
		NAMES[110] = "FDIV"; // -
		NAMES[111] = "DDIV"; // -
		NAMES[112] = "IREM"; // -
		NAMES[113] = "LREM"; // -
		NAMES[114] = "FREM"; // -
		NAMES[115] = "DREM"; // -
		NAMES[116] = "INEG"; // -
		NAMES[117] = "LNEG"; // -
		NAMES[118] = "FNEG"; // -
		NAMES[119] = "DNEG"; // -
		NAMES[120] = "ISHL"; // -
		NAMES[121] = "LSHL"; // -
		NAMES[122] = "ISHR"; // -
		NAMES[123] = "LSHR"; // -
		NAMES[124] = "IUSHR"; // -
		NAMES[125] = "LUSHR"; // -
		NAMES[126] = "IAND"; // -
		NAMES[127] = "LAND"; // -
		NAMES[128] = "IOR"; // -
		NAMES[129] = "LOR"; // -
		NAMES[130] = "IXOR"; // -
		NAMES[131] = "LXOR"; // -
		NAMES[132] = "IINC"; // visitIincInsn
		NAMES[133] = "I2L"; // visitInsn
		NAMES[134] = "I2F"; // -
		NAMES[135] = "I2D"; // -
		NAMES[136] = "L2I"; // -
		NAMES[137] = "L2F"; // -
		NAMES[138] = "L2D"; // -
		NAMES[139] = "F2I"; // -
		NAMES[140] = "F2L"; // -
		NAMES[141] = "F2D"; // -
		NAMES[142] = "D2I"; // -
		NAMES[143] = "D2L"; // -
		NAMES[144] = "D2F"; // -
		NAMES[145] = "I2B"; // -
		NAMES[146] = "I2C"; // -
		NAMES[147] = "I2S"; // -
		NAMES[148] = "LCMP"; // -
		NAMES[149] = "FCMPL"; // -
		NAMES[150] = "FCMPG"; // -
		NAMES[151] = "DCMPL"; // -
		NAMES[152] = "DCMPG"; // -
		NAMES[153] = "IFEQ"; // visitJumpInsn
		NAMES[154] = "IFNE"; // -
		NAMES[155] = "IFLT"; // -
		NAMES[156] = "IFGE"; // -
		NAMES[157] = "IFGT"; // -
		NAMES[158] = "IFLE"; // -
		NAMES[159] = "IF_ICMPEQ"; // -
		NAMES[160] = "IF_ICMPNE"; // -
		NAMES[161] = "IF_ICMPLT"; // -
		NAMES[162] = "IF_ICMPGE"; // -
		NAMES[163] = "IF_ICMPGT"; // -
		NAMES[164] = "IF_ICMPLE"; // -
		NAMES[165] = "IF_ACMPEQ"; // -
		NAMES[166] = "IF_ACMPNE"; // -
		NAMES[167] = "GOTO"; // -
		NAMES[168] = "JSR"; // -
		NAMES[169] = "RET"; // visitVarInsn
		NAMES[170] = "TABLESWITCH"; // visiTableSwitchInsn
		NAMES[171] = "LOOKUPSWITCH"; // visitLookupSwitch
		NAMES[172] = "IRETURN"; // visitInsn
		NAMES[173] = "LRETURN"; // -
		NAMES[174] = "FRETURN"; // -
		NAMES[175] = "DRETURN"; // -
		NAMES[176] = "ARETURN"; // -
		NAMES[177] = "RETURN"; // -
		NAMES[178] = "GETSTATIC"; // visitFieldInsn
		NAMES[179] = "PUTSTATIC"; // -
		NAMES[180] = "GETFIELD"; // -
		NAMES[181] = "PUTFIELD"; // -
		NAMES[182] = "INVOKEVIRTUAL"; // visitMethodInsn
		NAMES[183] = "INVOKESPECIAL"; // -
		NAMES[184] = "INVOKESTATIC"; // -
		NAMES[185] = "INVOKEINTERFACE"; // -
		NAMES[186] = "INVOKEDYNAMIC"; // visitInvokeDynamicInsn
		NAMES[187] = "NEW"; // visitTypeInsn
		NAMES[188] = "NEWARRAY"; // visitIntInsn
		NAMES[189] = "ANEWARRAY"; // visitTypeInsn
		NAMES[190] = "ARRAYLENGTH"; // visitInsn
		NAMES[191] = "ATHROW"; // -
		NAMES[192] = "CHECKCAST"; // visitTypeInsn
		NAMES[193] = "INSTANCEOF"; // -
		NAMES[194] = "MONITORENTER"; // visitInsn
		NAMES[195] = "MONITOREXIT"; // -
		NAMES[197] = "MULTIANEWARRAY"; // visitMultiANewArrayInsn
		NAMES[198] = "IFNULL"; // visitJumpInsn
		NAMES[199] = "IFNONNULL"; // -
	}
	
	/**
	 * Gets the name of a JVM-opcode.
	 * @param opcode opcode
	 * @return name, e.g. "ALOAD_0"
	 */
	public static final String lookup(final int opcode) {
		String name = (opcode >= 0 && opcode < NAMES.length) ? NAMES[opcode] : null;
		if (name == null) {
			name = String.format("Opcode_%02x", Integer.valueOf(opcode));
		}
		return name;
	}
}
