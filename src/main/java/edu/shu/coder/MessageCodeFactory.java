package edu.shu.coder;

import java.nio.charset.Charset;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
/**
 * 编解码工厂类
 * @author Zhy
 *	编解码器用来处理所有消息的统一处理过程
 *	一般是协议的拆包和打包
 */
public class MessageCodeFactory implements ProtocolCodecFactory{
	//这边定义编码和解码器 ，分别在接受和发送时使用
	private final MessageDecoder decoder;
	private final MessageEncoder encoder;
	
	public MessageCodeFactory(Charset charset) {
		encoder = new MessageEncoder(charset);
		decoder = new MessageDecoder(charset);
	}
	
	public MessageCodeFactory() {
		Charset charset = Charset.forName("utf-8");
		encoder = new MessageEncoder(charset);
		decoder = new MessageDecoder(charset);
	}
	
	public ProtocolEncoder getEncoder(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		return encoder;
	}

	public ProtocolDecoder getDecoder(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		return decoder;
	}

}
