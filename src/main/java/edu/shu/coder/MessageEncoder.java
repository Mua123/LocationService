package edu.shu.coder;

import java.nio.charset.Charset;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;


public class MessageEncoder extends ProtocolEncoderAdapter {
	private final Charset charset;

	public Logger logger = Logger.getLogger(MessageEncoder.class);

	public MessageEncoder(Charset charset) {
		this.charset = charset;
	}

	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
		// TODO Auto-generated method stub
		// 编码 java对象编译为二进制流 对信息进行二进制操作
		byte[] msg = (byte[]) message;
		IoBuffer buf = IoBuffer.allocate(msg.length).setAutoExpand(true);
		buf.put(msg);
		buf.flip();
		out.write(buf);
	}

}
