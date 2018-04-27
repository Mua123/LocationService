package edu.shu.coder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

import edu.shu.entity.MessagePackage;
import edu.shu.utils.ConvertTool;
import edu.shu.utils.GenCRC;

public class MessageDecoder extends CumulativeProtocolDecoder {
	Logger logger = Logger.getLogger(MessageDecoder.class);
	private final AttributeKey CONTEXT = new AttributeKey(this.getClass(), "context");
	private final Charset charset;
	private int maxPackLength = 2048;

	public static Integer count = 0;

	public int getMaxPackLength() {
		return maxPackLength;
	}

	public void setMaxPackLength(int maxPackLength) {
		if (maxPackLength < 0) {
			throw new IllegalArgumentException("maxLength 参数错误" + maxPackLength);
		}
		this.maxPackLength = maxPackLength;
	}

	public MessageDecoder(Charset charset) {
		// TODO Auto-generated constructor stub
		// 输入的是字符集
		this.charset = charset;
	}

	public MessageDecoder() {
		// TODO Auto-generated constructor stub
		this(Charset.defaultCharset());
	}

	private MessagePackage ParePackage(IoBuffer slice, boolean isPackage1) {
		// TODO Auto-generated method stub
		slice.mark();
		byte[] GenMat = new byte[slice.limit() - 4];
		slice.get(GenMat);
		char gen = GenCRC.getCrc16(GenMat);
		slice.reset();
		MessagePackage pack = new MessagePackage();
		if (isPackage1) {
			pack.setLength(slice.get()&0xff + 0);
		} else {
			pack.setLength(slice.getChar() + 0);
		}
		pack.setProtocolCode(slice.get());
		logger.info("协议码" + Integer.toHexString(pack.getProtocolCode() & 0xff + 0));
		byte[] dest = new byte[pack.getLength() - 5];
		slice.get(dest);
		pack.setContext(dest);
		pack.setSequence(slice.getChar());
		pack.setCRC(slice.getChar());
		if (gen != pack.getCRC()) {
			logger.error("校验出错" + ConvertTool.bytesToHexString(GenMat));
			return null;
		}
		
		return pack;
	}

	// session被重置的时候关闭这边的 contex 并且释放消息
	@Override
	public void dispose(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		Context ctx = (Context) session.getAttribute(CONTEXT);
		if (ctx != null) {
			session.removeAttribute(CONTEXT);
		}
	}

	@Override
	public void setTransportMetadataFragmentation(boolean transportMetadataFragmentation) {
		// TODO Auto-generated method stub
		super.setTransportMetadataFragmentation(transportMetadataFragmentation);
	}

	// 获取context对象方法 上下文对象
	// 每条信号进行缓存 再一次tcp中间 这边的读取的流是一样的
	public Context getContext(IoSession session) {
		Context ctx = (Context) session.getAttribute(CONTEXT);
		if (ctx == null) {
			ctx = new Context();
			session.setAttribute(CONTEXT, ctx);
		}
		return ctx;
	}

	@Override
	protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		// TODO Auto-generated method stub
		// 转换我们的字节流到 对象
		// System.out.println("解码"+System.currentTimeMillis());
		String message = "";
		in.mark();
//		byte[] buff = new byte[in.limit()];
//		in.get(buff);
		logger.debug(in);
		in.reset();
		try {
			int startpos = -1;
			boolean IsPackage1 = false;
			byte prve;
			int length = 0;
			while (in.hasRemaining()) {
				char current;
				if (in.limit() - in.position() >= 2) {
					current = in.getChar();
				} else {
					break;
				}
				if (startpos ==-1 && current == MessagePackage.START_FLAG) {
					startpos = in.position();
					in.mark();
					length =in.get()&0xff;
					in.reset();
					IsPackage1 = true;
				} else if (current == MessagePackage.END_FLAG && startpos != -1&&(in.position()-startpos)>length) {
					int limit = in.limit();
					in.limit(in.position());
					in.position(startpos); 
					IoBuffer  slice= in.slice();
//					logger.error("receive from:"+((InetSocketAddress)session.getRemoteAddress()).getAddress().getHostAddress()+":"+ConvertTool.bytesToHexString());
					logger.error("receive from:"+((InetSocketAddress)session.getRemoteAddress()).getAddress().getHostAddress()+":"+slice);
					out.write(ParePackage(slice, IsPackage1));
					in.position(in.limit());
					in.limit(limit);
					return true;
				} else if (current == MessagePackage.START_FLAG_2) {
					startpos = in.position();
					in.mark();
					length =(in.get()&0xff)*256;
					length+=in.get()&0xff;
					byte[] by = ConvertTool.hexStringToBytes("0036");
					in.reset();
					IsPackage1 = false;
				} else {
					in.position(in.position() - 1);
				}
			}
			logger.info("startpos"+startpos);
			int pos = startpos - 2;
			in.position(pos>0?pos:0);
			logger.info("limit"+in.limit());
			in.limit(in.limit());
			return false;
		} catch (Exception e) {
			// TODO: handle exception
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			String exceptionStack = "";
			e.printStackTrace(pw);
			exceptionStack = sw.toString();
			logger.error(exceptionStack);
			return false;
		}
	}

	// 在 解码器的内部定义了一个 内容的解析器 其中很多属性无法理解
	private class Context {
		private final CharsetDecoder decoder;
		private IoBuffer buf;

		private Context() {
			decoder = charset.newDecoder();
			buf = IoBuffer.allocate(80).setAutoExpand(true);
		}

		public IoBuffer getBuf() {
			return buf;
		}

		public void setBuf(IoBuffer buf) {
			this.buf = buf;
		}

		public CharsetDecoder getDecoder() {
			return decoder;
		}

		public void append(IoBuffer in) {
			this.getBuf().put(in);
		}

		public void reset() {
			decoder.reset();
		}
	}
}
