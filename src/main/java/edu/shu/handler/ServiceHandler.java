package edu.shu.handler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.sql.Timestamp;

import org.apache.log4j.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.springframework.beans.factory.annotation.Autowired;

import edu.shu.dao.DeviceStatusDAO;
import edu.shu.dao.DeviceStatusRecentDAO;
import edu.shu.dao.LocationInfoDao;
import edu.shu.entity.DeviceStatus;
import edu.shu.entity.DeviceStatusRecent;
import edu.shu.entity.MessagePackage;
import edu.shu.utils.ConvertTool;
import edu.shu.utils.GenCRC;

public class ServiceHandler extends IoHandlerAdapter {
	Logger logger = Logger.getLogger(ServiceHandler.class);
	@Autowired
	public LocationInfoDao locationInfoDao;
	//维护所有状态的数据
	@Autowired
	public DeviceStatusDAO statusdao;
	//维护最新的状态数据
	@Autowired
	public DeviceStatusRecentDAO statusRecentDao;
	
	@Override
	public void sessionCreated(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		super.sessionCreated(session);
	}

	@Override
	public void sessionOpened(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		super.sessionOpened(session);
		System.out.println("open");
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		super.sessionClosed(session);
		System.out.println("close");
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
		// TODO Auto-generated method stub
		if(!session.isConnected()) {
			System.out.println("session没有连接");
		}
		System.out.println("关闭session");
		//该关闭不起作用
//		sessionClosed(session);
		//刷新完数据之后再关闭session
		session.closeOnFlush();
		super.sessionIdle(session, status);
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		// TODO Auto-generated method stub
		super.exceptionCaught(session, cause);
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		MessagePackage msg = (MessagePackage) message;
		String IMEI = (String) session.getAttribute("IMEI");
		logger.debug("size" + msg.getLength() + "数据包" + (Integer.toHexString(msg.getProtocolCode() & 0xff)) + ":"
				+ ConvertTool.bytesToHexString(msg.getContext()) + "校验码" + charToCRC(msg.getCRC()) + "序列"
				+ charToCRC(msg.getSeqence()));
		byte[] response = "".getBytes();
		try {
			switch (msg.getProtocolCode() & 0xff) {
			// GT710登录包
			case 0x01:
				logger.info("登录包");
				response = doLogin(msg, session);
				break;
			// GT710 心跳包
			case 0x23:
				logger.info("心跳包");
				response = doheartbeat(msg, session);
				break;
			}
		} catch (Exception e) {
			// TODO: handle exception
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			String exceptionStack = "";
			e.printStackTrace();
			exceptionStack = sw.toString();
			logger.error("出现问题", e);
		}

			// logger.info("数据处理结束"+System.currentTimeMillis());
			// Thread.sleep(8000);
			logger.error("sendto:"+((InetSocketAddress)session.getRemoteAddress()).getAddress().getHostAddress()+":"+ConvertTool.bytesToHexString(response));
			session.write(response);
		
	}

	@Override
	public void messageSent(IoSession session, Object message) throws Exception {
		// TODO Auto-generated method stub
		super.messageSent(session, message);
	}

	@Override
	public void inputClosed(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		super.inputClosed(session);
	}
	
	private byte[] doLogin(MessagePackage msg, IoSession session) {
		// TODO Auto-generated method stub
		// instruct thr IMEI code from byte
		// pass the crc check and do next
		// 终端id 的号码解析;
		byte[] buff = msg.getContext();
		byte[] IMEI2 = new byte[8];
		System.arraycopy(buff, 0, IMEI2, 0, 8);
		byte[] type = new byte[2];
		System.arraycopy(buff, 8, type, 0, 2);
		byte[] timezoo = new byte[2];
		System.arraycopy(buff, 10, timezoo, 0, 2);
		String imei = ConvertTool.bytesToHexString(IMEI2);
		imei = imei.substring(1);
		logger.info("IME码ID" + imei);
		session.setAttribute("IMEI", imei);
		String device = ConvertTool.bytesToHexString(type);
		logger.info("类型识别码:" + device);
		session.setAttribute("DEVICETYPE", device);
		String IMEI = ConvertTool.bytesToHexString(IMEI2);
		byte high = timezoo[0];
		byte low = timezoo[1];
		int west = low & 0x3;
		int qu = ((high & 0xff) * 16 + (low >>> 4)) / 100;
		logger.info(west == 1 ? "西时区" : "东时区" + qu + "区域");
		logger.info("处理结果或过程中的一些结果");
		StringBuilder strb = new StringBuilder();
		strb.append("7878");
		strb.append("0501");
		strb.append("0005");
		// GenCRC.getCrc16(ConvertTool.hexStringToBytes("05011122460"));
		char ch = GenCRC.getCrc16(ConvertTool.hexStringToBytes("05010005"));
		String Crc = Integer.toHexString(ch + 0).toUpperCase();
		strb.append(Crc);
		strb.append("0D0A");
		logger.info("设备" + IMEI + " 登录");
		byte[] response = ConvertTool.hexStringToBytes(strb.toString());
		return response;
	}
	
	public byte[] doheartbeat(MessagePackage msg, IoSession session) {
		// TODO Auto-generated method stub
		byte[] response;
		byte[] buff = msg.getContext();
		DeviceStatus statu = new DeviceStatus();
		byte termInfo = buff[0];
		String IMEI = (String) session.getAttribute("IMEI");
		String device = (String) session.getAttribute("DEVICETYPE");
		logger.info(IMEI);

		// 设置设备的IMEI码
		statu.setImei(IMEI);

		// 设备状态只有一位
		if ((termInfo & 0x80) == 0x80) {
			logger.info("油电断开");
			statu.setOilelectirc(true);
		} else {
			logger.info("油电接通");
			statu.setOilelectirc(false);
		}
		if ((termInfo & 0x40) == 0x40) {
			logger.info("Gps已定位");
			statu.setGpsstate(true);
		} else {
			logger.info("GPS未定位");
			statu.setGpsstate(false);
		}
		if ((termInfo & 0x01) == 0x01) {
			logger.info("设防");
			statu.setGuard(true);
		} else {
			statu.setGuard(false);
			logger.info("撤防");
		}
		if ((termInfo & 0x02) == 0x02) {
			statu.setAcc(true);
			logger.info("ACC高");
		} else {
			statu.setAcc(false);
			logger.info("ACC低");
		}
		if ((termInfo & 0x04) == 0x04) {
			statu.setCharging(true);
			logger.info("已接电源充电");
		} else {
			logger.info("未接电源充电");
			statu.setCharging(false);
		}

		// 设置电池
		byte[] butter = new byte[2];
		System.arraycopy(buff, 1, butter, 0, 2);
		int v = (butter[0] & 0xff) * 256 + (butter[1] & 0xff);
		float volt = (float) (v * 1.0 / 100.0);
		logger.info("电池的容量是" + volt);
		statu.setBattery(volt);

		byte Gsm = buff[3];
		statu.setGsm(Gsm + 0);
		statu.setImei(IMEI);
		switch (Gsm) {
		case 0x00:
			logger.info("无信号");
			break;
		case 0x01:
			logger.info("信号极弱");
			break;
		case 0x02:
			logger.info("信号较弱");
			break;
		case 0x03:
			logger.info("信号良好");
			break;
		case 0x04:
			logger.info("信号强");
			break;
		default:
			break;
		}

		// 扩展英文状态
		byte Lan = buff[5];
		switch (Lan) {
		case 0x01:
			logger.info("中文");
			statu.setLanguage(true);
			break;
		case 0x02:
			logger.info("英文");
			statu.setLanguage(false);
			break;
		default:
			break;
		}

		// 重新封装 返回
		StringBuilder sb = new StringBuilder();
		sb.append("7878");
		sb.append("05");
		sb.append("23");
		char c = msg.getSeqence();
		byte[] seq = charToByte(c);
		sb.append(GenerateSeq(ConvertTool.bytesToHexString(seq)));
		// sb.append("0100");
		String data = sb.substring(4);
		logger.info("校验的位置" + data);
		char ch = GenCRC.getCrc16(ConvertTool.hexStringToBytes(data));
		String Crc = Integer.toHexString(ch + 0).toUpperCase();
		sb.append(GenerateSeq(Crc));
		sb.append("0D0A");
		response = ConvertTool.hexStringToBytes(sb.toString());

		// 增加数据添加时间
		Timestamp stamp = new Timestamp(System.currentTimeMillis());
		statu.setDate_add(stamp);

		statu.setDevice_type(device);

		// 增加数据的序列时间
		long index = getIndexFromMessage(msg);
		
		if(statu!=null) {
			DeviceStatusRecent recent = new DeviceStatusRecent();
			recent.setImei(statu.getImei());
			recent.setDevice_type(statu.getDevice_type());
			recent.setBattery(statu.getBattery());
			recent.setGsm(statu.getGsm());
			recent.setOilelectirc(statu.getOilelectirc());
			recent.setGpsstate(statu.getGpsstate());
			recent.setCharging(statu.getCharging());
			recent.setAcc(statu.getAcc());
			recent.setGuard(statu.getGuard());
			recent.setLanguage(statu.getLanguage());
			recent.setDate_add(statu.getDate_add());
			statusRecentDao.updateDevice(recent);
		}

		statusdao.insertDevice(statu);
		return response;
	}

	/**
	 * 循环校验码 是2byte 使用char的形式存储 然后实际上 换算成16进制的字符串
	 * 
	 * @param ch
	 * @return
	 */
	public String charToCRC(char ch) {
		return ConvertTool.bytesToHexString(charToByte(ch));
	}
	
	public static byte[] charToByte(char c) {
		byte[] b = new byte[2];
		b[0] = (byte) ((c & 0xFF00) >> 8);
		b[1] = (byte) (c & 0xFF);
		return b;
	}
	
	// 只有一个数字的话要加0
	public String GenerateSeq(String str) {
		while (str.length() < 4) {
			str = "0" + str;
		}
		return str;
	}

	/**
	 * 从原始的包中获取 到序列号码
	 * 
	 * @param msg
	 *            传入数据包的解析类
	 * @return 返回抽取出来的 序列号
	 */
	private long getIndexFromMessage(MessagePackage msg) {
		// TODO Auto-generated method stub
		long res = Long.parseLong(charToCRC(msg.getSeqence()), 16);
		return res;
	}
	
}
