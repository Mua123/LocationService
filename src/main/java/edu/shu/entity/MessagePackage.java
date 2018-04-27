package edu.shu.entity;

import java.util.Arrays;

//这边指的是总体的包 那么要包含其实位置和后续位置还是 不需要
//这边转换为包 之后 可以在后面的数据中使用 flag 进行解析
//handler 中根据flag 解析数据 返回
/**
 * @author Administrator
 *
 */
public class MessagePackage {
	int length;
	byte protocolCode;
	byte[] context;
	char sequence;
	char CRC;
	public static final char START_FLAG = 0x7878;
	public static final char END_FLAG = 0x0D0A;
	public static final char START_FLAG_2 = 0x7979;
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}
	public byte getProtocolCode() {
		return protocolCode;
	}
	public void setProtocolCode(byte protocolCode) {
		this.protocolCode = protocolCode;
	}
	public byte[] getContext() {
		return context;
	}
	public void setContext(byte[] context) {
		this.context = context;
	}

	public char getSequence() {
		return sequence;
	}
	public void setSequence(char sequence) {
		this.sequence = sequence;
	}
	public char getCRC() {
		return CRC;
	}
	public void setCRC(char cRC) {
		CRC = cRC;
	}
	@Override
	public String toString() {
		return "MessagePackage [length=" + length + ", protocolCode="
				+ protocolCode + ", context=" + Arrays.toString(context)
				+ ", sequence=" + sequence + ", CRC=" + CRC + "]";
	}
	
}
