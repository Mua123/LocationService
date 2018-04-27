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

/**
 * 解码器
 * @author Zhy
 *	将协议中协议头去除，协议中检验部分检验并去除，将有效信息保留
 */

public class MessageDecoder3 extends CumulativeProtocolDecoder {
	Logger logger = Logger.getLogger(MessageDecoder3.class);
	// context用来保存当前读取的状态。在一个包在两个接受过程中才被完整接受时，十分重要。
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

	public MessageDecoder3(Charset charset) {
		// TODO Auto-generated constructor stub
		// 输入的是字符集
		this.charset = charset;
	}

	public MessageDecoder3() {
		// TODO Auto-generated constructor stub
		this(Charset.defaultCharset());
	}

	private MessagePackage ParePackage(IoBuffer slice, boolean isPackage1) {
		// TODO Auto-generated method stub
		slice.mark();
		byte[] GenMat = new byte[slice.limit() - 4];	//减去结束位和  错误校验位4个byte
		
		slice.get(GenMat);
		char gen = GenCRC.getCrc16(GenMat);			//校验位
		slice.reset();
		MessagePackage pack = new MessagePackage();
		if (isPackage1) {
			pack.setLength(slice.get()&0xff + 0);		//长度
		} else {
			pack.setLength(slice.getChar() + 0);
		}
		pack.setProtocolCode(slice.get());			//协议号
		logger.info("协议码" + Integer.toHexString(pack.getProtocolCode() & 0xff + 0));
		byte[] dest = new byte[pack.getLength() - 5];			//减去协议号信息序列号错误校验
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


	// 获取context对象方法 上下文对象
	// 每条信号进行缓存 再一次tcp中间 这边的读取的流是一样的
	//通过IoSession.setAttribute和IoSession.getAttribute的保存和得到保存数据的对象
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
		try {
			System.out.println("调用");
			//获取上下文状态变量
			Context ctx = getContext(session);
			 
//			in.position(ctx.getPosition());
			//状态
			int startPos =  -1;			//	起始位置
			boolean packageSort = false;	//0x7878
			
			int length = 0;
			while (in.hasRemaining()) {
				if(in.remaining() >= maxPackLength) {
					logger.error("接收数据长度超过包长度");
					throw new Exception("接受数据长度超过包长度，数据过长");
				}
				char current;	//一个char两个字节
				if (in.limit() - in.position() >= 2) {
					current = in.getChar();
					System.out.println(Integer.toHexString(current & 0xFFFF));
					System.out.println(in.position());
				} else {
					return false;			//如果读不到两个字节则表示需要继续读
				}
				if (startPos == -1 && current == MessagePackage.START_FLAG) {
					startPos = in.position();	//两个字节等于起始位
					in.mark();
					length =in.get()&0xff;	//包长度,get(向前挪了一个byte)
					in.reset();
					packageSort = true;
//					ctx.setStartPos(startPos);
//					ctx.setPackageSort(Context.package78);
				} else if (current == MessagePackage.END_FLAG && startPos != -1&&(in.position()-startPos)>length) {
					int limit = in.limit();
					in.limit(in.position());		//重新整理iobuffer的起点和终点
					in.position(startPos); 
					IoBuffer slice= in.slice();
					logger.error("receive from:"+((InetSocketAddress)session.getRemoteAddress()).getAddress().getHostAddress()+":"+slice);
					out.write(ParePackage(slice, packageSort));//IsPackage1，设备种类
//					ctx.reset();
					in.position(in.limit());			//恢复原状
					in.limit(limit);
					return true;//返回ture应该会把读过的删除
				} else if (current == MessagePackage.START_FLAG_2) {//两种消息长度不同
					startPos = in.position();
					in.mark();
					length =(in.get()&0xff)*256;
					length+=in.get()&0xff;
					in.reset();
					packageSort = false;
//					ctx.setStartPos(startPos);
//					ctx.setPackageSort(Context.package79);
				} else {
					in.position(in.position() - 1);
				}
			}//全部读完，没有结束，等待
			int pos = startPos - 2;				//将起始位也包含进去
			in.position(pos>0?pos:0);
			in.limit(in.limit());
//			ctx.setPosition(in.position());
			return false;			//消息未结束，继续等待
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

	// 在 解码器的内部定义了一个状态变量类，用来保存状态变量
	private class Context {

//		private int position = 0;
		
		private int startPos = -1;
		
		private boolean packageSort = false; 
		
		private IoBuffer ioBuffer = IoBuffer.allocate(2048).setAutoExpand(true);
		
		private final static boolean package78 = true;
		private final static boolean package79 = false;
		
		public Context() {
			
		}


		
		public IoBuffer getIoBuffer() {
			return ioBuffer;
		}



		public void setIoBuffer(IoBuffer ioBuffer) {
			this.ioBuffer = ioBuffer;
		}



		public int getStartPos() {
			return startPos;
		}

		public void setStartPos(int startPos) {
			this.startPos = startPos;
		}

		public boolean getPackageSort() {
			return packageSort;
		}

		public void setPackageSort(boolean packageSort) {
			this.packageSort = packageSort;
		}

		public void reset() {
			this.ioBuffer.clear();
			this.startPos = -1;
			this.packageSort = false;
		}
	}
}
