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

public class MessageDecoder2 extends CumulativeProtocolDecoder {
	Logger logger = Logger.getLogger(MessageDecoder3.class);
	// context用来保存当前读取的状态。主要应用在一个信息包不在一次传输中传输完成
	private final AttributeKey CONTEXT = new AttributeKey(this.getClass(), "context");
	private final Charset charset;
	private int maxPackLength = 2048;

	public int getMaxPackLength() {
		return maxPackLength;
	}

	public void setMaxPackLength(int maxPackLength) {
		if (maxPackLength < 0) {
			throw new IllegalArgumentException("maxLength 参数错误" + maxPackLength);
		}
		this.maxPackLength = maxPackLength;
	}

	public MessageDecoder2(Charset charset) {
		// TODO Auto-generated constructor stub
		// 输入的是字符集
		this.charset = charset;
	}

	public MessageDecoder2() {
		// TODO Auto-generated constructor stub
		this(Charset.defaultCharset());
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
	// 能够有效保证一个session使用一个对象
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
//			System.out.println("调用");
			//获取上下文状态变量
			Context ctx = getContext(session);
			
			//将context中保存的状态取出
			int length = ctx.getLength();
			byte protocolCode = ctx.getProtocolCode();
			byte[] context = ctx.getContext();
			byte[] sequence = ctx.getSequence();
			byte[] CRC = ctx.getCRC();
			
			boolean packageSort =  ctx.getPackageSort();
			int partNum = ctx.getPartNum();
			int contextPos = ctx.getContextPos();
			
			byte mark = ctx.getMark();
			
			char genCRC = ctx.getGenCRC();
			
			IoBuffer CRCBuffer = ctx.getCRCBuffer();
			IoBuffer messageBuffer = ctx.getMessageBuffer();
			
			
			while(in.hasRemaining()) {
				//一个字节一个字节读入
				byte b = in.get();
				
				//第一部分：判断起始位
				if(partNum == 0 && (b == 0x78 || b == 0x79)) {
					if(mark == 0x00) {			//出现起始位第一位
						mark = b;					//将起始位标记打开，并保存起始位第一位
						ctx.setMark(mark);
					}
					else {
						if(b == mark) {				//判断下一位是不是相同的。如果相同则该处为疑似起始位置
							switch(b) {
								case 0x78:							//0x78 0x78开头的协议包
									packageSort = Context.PACKAGE78;
									ctx.setPackageSort(packageSort);
									break;
								case 0x79:							//0x79 0x79开头的协议包
									packageSort = Context.PACKAGE79;
									ctx.setPackageSort(packageSort);
									break;
							}
							partNum++;					//找到疑似起始位置，开始解析下一部分
						}else {						//如果下一位不相同，则表示该位置不是起始位置，将读取位置退后一位
							in.position(in.position()-1);
						}							//无论是否为起始位，都需要将标志位mark还原
						mark = 0x00;
						ctx.setMark(mark);
					}
				//第二部分：解析包长度
				}else if(partNum == 1) {					//解析包长度
					if(packageSort == Context.PACKAGE78) {
						length = b & 0xff;
						partNum++;
						CRCBuffer.clear();
					}else {
						if(length != -1) {					//length不为零，则长度已经读入一位；
							partNum++;
							length = length * 256 + b&0xff;
						}else {
							length = b&0xff;
							CRCBuffer.clear();
						}
						
					}
					ctx.setLength(length);
				//第三部分：解析协议号
				}else if(partNum == 2) {					//解析协议号
					protocolCode = b;
					ctx.setProtocolCode(protocolCode);
					partNum++;
					if(length - 5 == 0) {
						partNum++;
					}
				//第四部分：解析信息内容
				}else if(partNum == 3) {					//解析信息内容
					if(context == null) 
						context = new byte[length-5];				//初始化为空，因此根据读取的长度创建数组
					context[contextPos] = b;						//将信息内容按byte存入
					contextPos++;
					ctx.setContext(context);
					ctx.setContextPos(contextPos);
					if(contextPos >= context.length) {				//判断是否数据已存完
						partNum++; 
					}
				//第五部分：解析信息序列号
				}else if(partNum == 4) {					//解析信息序列号
					if(sequence == null) {							//初始化为空，因此创建长度为2的数组存储
						sequence = new byte[2];
						sequence[0] = b;
					}else {											//读取到第二位时，读取数据结束
						sequence[1] = b;
						partNum++;
					}
					ctx.setSequence(sequence);
				//第六部分：解析错误校验
				}else if(partNum == 5) {					//解析错误校验
					if(CRC == null) {								//初始化为空，因此创建长度为2的数组存储
						CRC = new byte[2];
						byte[] CRCBytes;
						if(packageSort == Context.PACKAGE78)
							CRCBytes = new byte[length - 2 + 1];		//创建用来存储计算校验码的所有byte，从包长度到信息序列号。长度为协议中的长度-2位的错误校验位+1位或2位的包长度
						else
							CRCBytes = new byte[length - 2 + 2];	
						CRCBuffer.flip();
						CRCBuffer.get(CRCBytes);
						genCRC = GenCRC.getCrc16(CRCBytes);					//生成校验位
						ctx.setGenCRC(genCRC);
						CRC[0] = b;											//存入校验位第一位
					}else {
						CRC[1] = b;											//存入校验位第二位
						if (genCRC != (char)((CRC[0]<<8) + (CRC[1]&0xff))) {		//如果校验位与计算的校验位不同，则报错
							logger.error("校验出错");
							messageBuffer.put(b);							
							ctx.setMessageBuffer(messageBuffer);
							in = reset(in, messageBuffer);					//重新设置ioBuffer中的数据
							ctx.reset();									//状态变量重置
							return true;									//通知工厂，一次读取完成，需要新的读取
						}
						partNum++;
					}
					ctx.setCRC(CRC);
				//第七部分：解析停止位
				}else if(partNum == 6 ) {					//解析停止位
					if(b == 0x0D) {											//停止位第一位
						mark = 0x0D;
						ctx.setMark(mark);
					}else if(b == 0x0A && mark == 0x0D) {							//停止位第二位
						MessagePackage message = new MessagePackage();				//全部读取结束，将数据整理为对象，发送给后面的处理流程
						message.setLength(length);
						message.setProtocolCode(protocolCode);
						message.setContext(context);
						message.setSequence((char)((sequence[0]<<8) + (sequence[1]&0xff)));
						message.setCRC((char)((CRC[0]<<8) + (CRC[1]&0xff)));
						out.write(message);
						ctx.reset();												//状态变量重置
						return true;												//通知工厂，一次读取完成，需要新的读取
					}else {												//结束位错误时，刷新，重新读取
						messageBuffer.put(b);							
						ctx.setMessageBuffer(messageBuffer);
						in = reset(in, messageBuffer);					//重新设置ioBuffer中的数据
						ctx.reset();									//状态变量重置
						return true;									//通知工厂，一次读取完成，需要新的读取
					}
				}
				if(partNum != 0) {										//将起始位开始的数据放入messageBuffer，用于发生错误时重新遍历
					messageBuffer.put(b);
					ctx.setMessageBuffer(messageBuffer);
				}
				CRCBuffer.put(b);										//将CRC校验需要的数据放入
				ctx.setCRCBuffer(CRCBuffer);
				ctx.setPartNum(partNum);								//将当前读取到的部分放入
			}
			
			return false;
	}

	/**
	 * 重新整理IoBuffer的数据
	 * @param in
	 * @param messageBuffer
	 * @return
	 * 	将已经解析的数据除去起始位，拼接上还未读取的数据，合成新的IoBuffer
	 * 	此处可以避免遗漏数据
	 */
	private IoBuffer reset(IoBuffer in, IoBuffer messageBuffer) {				
		//创建新的IoBuffer
		IoBuffer resetBuffer = IoBuffer.allocate(2048).setAutoExpand(true);
		
		messageBuffer.flip();
		//将两部分数据存入新的buffer
		resetBuffer.put(messageBuffer);
		resetBuffer.put(in.slice());
		resetBuffer.flip();
		
		//将in中的数据替换为新组合的数据
		in.clear();
		in.put(resetBuffer);
		in.flip();
		//将position标签移动一位，防止工厂判断没有消费数据，而引发报错。第一位是起始位的后一位，因此无影响
		in.position(in.position()+1);
		return in;
	}

	/**
	 * 该类用来保存读取过程中的所有状态
	 * @author Zhy
	 *
	 */
	private class Context {

		private int partNum = 0;						//当前读取的部分数

		//五个状态，即将存入MessagePackage类，向后传递的数据
		private int length = -1;						//协议包长度
		private byte protocolCode = 0x00;				//协议号
		private byte[] context = null;					//信息内容				
		private byte[] sequence = null;					//信息序列号
		private byte[] CRC = null;						//CRC校验位
		
		private int contextPos = 0;						//信息内容已读取的位数					
		
		private char genCRC = 0x00;						//根据消息内容计算的校验码
		private byte mark = 0x00;						//起始位或者结束位的标志位
		
		private IoBuffer CRCBuffer = IoBuffer.allocate(2048).setAutoExpand(true);				//存放用来计算CRC校验的所需的数据
		
		private IoBuffer messageBuffer = IoBuffer.allocate(2048).setAutoExpand(true);			//存放除起始位第一位以外的消息内容，便于还原
		
		private boolean packageSort = PACKAGE78;		//协议包的种类，共两种
		
		private final static boolean PACKAGE78 = true;				//起始位为0x780x78的协议包
		private final static boolean PACKAGE79 = false;				//起始位为0x790x79的协议包
		
		public Context() {
			
		}
		
		/**
		 * 将状态重置，开始新的读取
		 */
		public void reset() {
			this.CRCBuffer.clear();
			this.messageBuffer.clear();
			
			this.partNum = 0;

			this.length = -1;
			this.protocolCode = 0x00;
			this.context = null;
			this.sequence = null;
			this.CRC = null;
			
			
			this.contextPos = 0;
			this.mark = 0x00;
			
			this.genCRC = 0x00;
			
			this.packageSort = PACKAGE78;
			
		}

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


		public byte[] getCRC() {
			return CRC;
		}

		public void setCRC(byte[] CRC) {
			this.CRC = CRC;
		}

		public byte[] getSequence() {
			return sequence;
		}

		public void setSequence(byte[] sequence) {
			this.sequence = sequence;
		}


		public IoBuffer getCRCBuffer() {
			return CRCBuffer;
		}

		public void setCRCBuffer(IoBuffer CRCBuffer) {
			this.CRCBuffer = CRCBuffer;
		}

		public IoBuffer getMessageBuffer() {
			return messageBuffer;
		}

		public void setMessageBuffer(IoBuffer messageBuffer) {
			this.messageBuffer = messageBuffer;
		}

		public int getPartNum() {
			return partNum;
		}

		public void setPartNum(int partNum) {
			this.partNum = partNum;
		}

		public byte getMark() {
			return mark;
		}

		public void setMark(byte mark) {
			this.mark = mark;
		}

		public boolean getPackageSort() {
			return packageSort;
		}

		public void setPackageSort(boolean packageSort) {
			this.packageSort = packageSort;
		}

		public int getContextPos() {
			return contextPos;
		}

		public void setContextPos(int contextPos) {
			this.contextPos = contextPos;
		}

		public char getGenCRC() {
			return genCRC;
		}

		public void setGenCRC(char genCRC) {
			this.genCRC = genCRC;
		}
		
		
	}
}
