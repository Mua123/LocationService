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
		
		logger.debug("size:" + msg.getLength() + "; 协议号:" + (Integer.toHexString(msg.getProtocolCode() & 0xff)) + "; 信息内容:"
				+ ConvertTool.bytesToHexString(msg.getContext()) + "; 校验码:" + charToCRC(msg.getCRC()) + "; 信息序列号:"
				+ charToCRC(msg.getSequence()));
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
			case 0x26:
				logger.info("报警包");
				response = Alarm(msg, session);
				break;
			case 0x28:
				logger.info("LBS包");
				response = LBS(msg, session);
				break;
			case 0x22:
				logger.info("GPS包");
				response = GPS(msg, session);
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
		//取出消息的信息内容
		byte[] context = msg.getContext();
		
		//该协议包中有三个数据需要取出
		
		//定义三个数组分别存放
		byte[] IMEIArr = new byte[8];
		byte[] deviceTypeArr = new byte[2];
		byte[] timeZooArr = new byte[2];
		
		//将消息内容分别存入三个数组
		/**
		 * System.arraycopy(arr1, arrStartPos, arr2, arrStartPos, length)方法
		 * 将数组1(arr1）中从arrStartPos处开始取长度为length的数据，放入数组2(arr2），从arrStartPos处开始
		 * 从context中从下标0开始取8个元素，放入IMEIArr中，从下标0开始存放
		 * System.arraycopy(context, 0, IMEIArr, 0, 8);
		 */
		
		int pos = 0;						//定义为已读取的位数
		System.arraycopy(context, pos, IMEIArr, 0, IMEIArr.length);
		pos += IMEIArr.length;
		System.arraycopy(context, pos, deviceTypeArr, 0, deviceTypeArr.length);
		pos += deviceTypeArr.length;
		System.arraycopy(context, pos, timeZooArr, 0, timeZooArr.length);
		pos += timeZooArr.length;
		
		/**
		 * 解析IMEI
		 */
		//直接将byte数组转成16进制字符串
		String IMEIStr = ConvertTool.bytesToHexString(IMEIArr);
		//将第一位的0去掉，参数1表示从下标1截取到结尾
		IMEIStr = IMEIStr.substring(1);
		logger.info("***************登录包解析************************");
		logger.info("登录设备的IMEI码:" + IMEIStr);
		
		//将解析出来的IMEI存到session中，这个session将一直存在，直至设备断开连接
		session.setAttribute("IMEI", IMEIStr);
		
		/**
		 * 解析类型识别码
		 */
		
		String deviceType = ConvertTool.bytesToHexString(deviceTypeArr);
		logger.info("类型识别码:" + deviceType);
		session.setAttribute("DEVICETYPE", deviceType);
		
		/**
		 * 解析时区语言
		 */
		byte highTimeZoo = timeZooArr[0];
		byte lowTimeZoo = timeZooArr[1];
		
		// 0x08二进制表示为0000 1000
		//这里将lowTimeZoo的bit4取出
		int UT = (lowTimeZoo & 0x08) >> 3;
		
		//取去时区
		int timeZooNum = ((highTimeZoo & 0xff) * 16 + (lowTimeZoo >>> 4)) / 100;
		
		//判断东西时区和具体时区
		logger.info(UT == 1 ? "时区:西" + timeZooNum + "区" : "时区:东" + timeZooNum + "区");
		
		/**
		 * 回复信息的构建
		 */
		logger.info("***************登录包回复************************");
		StringBuilder strBuilder = new StringBuilder();
		//添加起始位
		strBuilder.append("7878");
		
		//添加包长度
		strBuilder.append("05");
		
		//添加协议号
				strBuilder.append("01");
				
				//添加信息序列号
				strBuilder.append("0005");
				
				//计算出CRC值
				String CRCString = strBuilder.substring(4, strBuilder.length());
				char CRCValue = GenCRC.getCrc16(ConvertTool.hexStringToBytes(CRCString));
				String CRC = Integer.toHexString(CRCValue + 0).toUpperCase();
				//添加错误校验位
				strBuilder.append(CRC);
				//添加停止位
				strBuilder.append("0D0A");
				InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
				logger.error("登录包回复：IP["+address.getAddress()+":"+ address.getPort() +"]: " + strBuilder.toString());
				logger.info("**********************************************");
				//将字符串转化为byte数组
				byte[] response = ConvertTool.hexStringToBytes(strBuilder.toString());
				return response;
			}
	
	/*public byte[] doheartbeat(MessagePackage msg, IoSession session) {
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
		char c = msg.getSequence();
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
*/
	public byte[] doheartbeat(MessagePackage msg, IoSession session){

		byte[]context = msg.getContext();

		byte[] termInfoArr = new byte[1];
		byte[] butterArr = new byte[2];
		byte[] GsmArr = new byte[1];
		byte[] LanArr = new byte[2];

		int pos = 0;
		System.arraycopy(context,pos,termInfoArr,0,termInfoArr.length);
		pos += termInfoArr.length;
		System.arraycopy(context,pos,butterArr,0,butterArr.length);
		pos += butterArr.length;
		System.arraycopy(context,pos,GsmArr,0,GsmArr.length);
		pos += GsmArr.length;
		System.arraycopy(context,pos,LanArr,0,LanArr.length);
		pos += LanArr.length;
		
		/**
		 * 解析信息内容
		 */

		/*String termInfo = ConvertTool.bytesToHexString(termInfoArr);*/
		
		logger.info("***************心跳包解析************************");
		
		byte termInfo1 = termInfoArr[0];
		if ((termInfo1 & 0x80) == 0x80) {
				logger.info("油电断开");
				
			} else {
				logger.info("油电接通");
				
			}
			if ((termInfo1 & 0x40) == 0x40) {
				logger.info("Gps已定位");
				
			} else {
				logger.info("GPS未定位");
				
			}
			if ((termInfo1 & 0x01) == 0x01) {
				logger.info("设防");
				
			} else {
				
				logger.info("撤防");
			}
			if ((termInfo1 & 0x02) == 0x02) {
				
				logger.info("ACC高");
			} else {
				
				logger.info("ACC低");
			}
			if ((termInfo1 & 0x04) == 0x04) {
				
				logger.info("已接电源充电");
			} else {
				logger.info("未接电源充电");
				
			}
			logger.info("心跳包信息内容：" + termInfo1);

		/**
		 * 解析电压等级
		 */
		
		/*String butter = ConvertTool.bytesToHexString(butterArr);
*/
		int v = (butterArr[0] & 0xff) * 256 + (butterArr[1] & 0xff);
		float volt = (float) (v * 1.0 / 100.0);
		logger.info("电池的容量是" + volt);
		
		/**
		 * 解析Gsm信号强度
		 */
		
		/*String Gsm = ConvertTool.bytesToHexString(GsmArr);*/
		byte Gsm = GsmArr[0];
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

		/**
		 * 解析语言状态
		 */
/*		String Lan = ConvertTool.bytesToHexString(LanArr);
*/		
		byte Lan = LanArr[1];
		switch (Lan) {
			case 0x01:
				logger.info("中文");
				break;
			case 0x02:
				logger.info("英文");
				break;
			default:
				break;
			}

		/**
		 * 回复信息的构建
		 */
		
		logger.info("***************心跳包回复************************");
		StringBuilder strBuilder = new StringBuilder();
		//添加起始位
		strBuilder.append("7878");
			
		//添加包长度
		strBuilder.append("05");
			
		//添加协议号
		strBuilder.append("23");
			
		//添加信息序列号
		strBuilder.append(msg.getSequence());
		
		//计算出CRC值
		String CRCString = strBuilder.substring(4, strBuilder.length());
		char CRCValue = GenCRC.getCrc16(ConvertTool.hexStringToBytes(CRCString));
		String CRC = Integer.toHexString(CRCValue + 0).toUpperCase();
		//添加错误校验位
		strBuilder.append(CRC);
		//添加停止位
		strBuilder.append("0D0A");
		InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
		logger.error("心跳包回复：IP["+address.getAddress()+":"+ address.getPort() +"]: " + strBuilder.toString());
		logger.info("**********************************************");
		//将字符串转化为byte数组
		byte[] response = ConvertTool.hexStringToBytes(strBuilder.toString());
		return response;
		}	
	
	public byte[] Alarm(MessagePackage msg, IoSession session){

		byte[]context = msg.getContext();

		byte[]dateInfoArr = new byte[6];
		byte[]satArr = new byte[1];
		byte[]LatArr = new byte[4];
		byte[]LngArr = new byte[4];
		byte[]VelArr = new byte[1];
		byte[]HeadingArr = new byte[2];
		byte[]LBSlenArr = new byte[1];
		byte[]MCCArr = new byte[2];
		byte[]MNCArr = new byte[1];
		byte[]LACArr = new byte[2];
		byte[]ClDArr = new byte[3];
		byte[]termInfoArr = new byte[1];
		byte[]butterArr = new byte[1];
		byte[]GSMArr = new byte[1];
		byte[]alarmContArr = new byte[2];
		

		int pos = 0;
		System.arraycopy(context,pos,dateInfoArr,0,dateInfoArr.length);
		pos += dateInfoArr.length;
		System.arraycopy(context,pos,satArr,0,satArr.length);
		pos += satArr.length;
		System.arraycopy(context,pos,LatArr,0,LatArr.length);
		pos += LatArr.length;
		System.arraycopy(context,pos,LngArr,0,LngArr.length);
		pos += LngArr.length;
		System.arraycopy(context,pos,VelArr,0,VelArr.length);
		pos += VelArr.length;
		System.arraycopy(context,pos,HeadingArr,0,HeadingArr.length);
		pos += HeadingArr.length;
		System.arraycopy(context,pos,LBSlenArr,0,LBSlenArr.length);
		pos += LBSlenArr.length;
		System.arraycopy(context,pos,MCCArr,0,MCCArr.length);
		pos += MCCArr.length;
		System.arraycopy(context,pos,MNCArr,0,MNCArr.length);
		pos += MNCArr.length;
		System.arraycopy(context,pos,LACArr,0,LACArr.length);
		pos += LACArr.length;
		System.arraycopy(context,pos,ClDArr,0,ClDArr.length);
		pos += ClDArr.length;
		System.arraycopy(context,pos,termInfoArr,0,termInfoArr.length);
		pos += termInfoArr.length;
		System.arraycopy(context,pos,butterArr,0,butterArr.length);
		pos += butterArr.length;
		System.arraycopy(context,pos,GSMArr,0,GSMArr.length);
		pos += GSMArr.length;
		System.arraycopy(context,pos,alarmContArr,0,alarmContArr.length);
		pos += alarmContArr.length;
	

		/**
		 * 解析日期时间信息
		 */

		logger.info("***************报警包解析************************");
		
		
		int year = dateInfoArr[0] & 0xff;
		int month = dateInfoArr[1] & 0xff;
		int day = dateInfoArr[2] & 0xff;
		int h = dateInfoArr[3] & 0xff;
		int min = dateInfoArr[4] & 0xff;
		int s = dateInfoArr[5] & 0xff;
		logger.info("时间是："+ year +"年" + month + "月" + day + "日" + h + "时" + min + "分" + s + "秒");

		
		/**
		 * 解析GPS信息卫星数
		 */
		byte sat = satArr[0]; 
		int GPSlen = sat & 0xf0 ;
		float len = (float) (GPSlen * 1.0 / 16.0);
		int satNum = sat & 0x0f ;
		float num = (float) (satNum * 1.0);
		logger.info("GPS信息长度是" + len + "参与定位卫星数是" + num);

		
		/**
		 * 解析纬度
		 */
		
		int l = (LatArr[0] & 0xff) * 16777216 + (LatArr[1] & 0xff) * 65536+ (LatArr[2] & 0xff) * 256 +(LatArr[3] & 0xff);

		float lat = (float)(l*1.0/1800000.0);
		logger.info("纬度是" + lat);

		
		/**
		 * 解析经度
		 */
		
		int ln = (LngArr[0] & 0xff) * 16777216 + (LngArr[1] & 0xff) * 65536+ (LngArr[2] & 0xff) * 256 +(LngArr[3] & 0xff);

		float lng = (float)(ln*1.0/1800000.0);
		logger.info("经度是" + lng);
		

		/**
		 * 解析速度
		 */

		int v = VelArr[0] & 0xff;
		float vel = (float)(v*1.0);
		logger.info("速度是" + vel);

		
		/**
		 * 解析航向状态
		 */
		
		byte highHeading = HeadingArr[0];
		byte lowHeading = HeadingArr[1];
		
		//将高字节bit6实时、差分定位信息取出
		int RT = (highHeading & 0x20) >> 5;

		//将高字节bit5定位已否信息取出
		int ifPos = (highHeading & 0x10) >> 4;

		//将高字节bit4东西经信息取出
		int Lon = (highHeading & 0x08) >> 3;

		//将高字节bit3南北纬信息取出
		int Ltt = (highHeading & 0x04) >> 2;

		//判断航向
		int Head = (highHeading & 0x03) * 256 + (lowHeading & 0xff);

		if ( ifPos == 1 ){
			logger.info( "GPS已定位");
		} else{
			logger.info("GPS未定位");
		}
		if (RT == 1){
			logger.info("差分GPS");
		} else{
			logger.info("实时GPS");
		}
		if ( Lon == 1){
			logger.info("西经");
		} else{
			logger.info("东经");
		}
		if ( Ltt ==1){
			logger.info("北纬");
		}else{
			logger.info("南纬");
		}
		logger.info("航向"+Head+"°");

		/**
		 * 解析LBS长度
		 */

		String LBSlenStr = ConvertTool.bytesToHexString(LBSlenArr);
		logger.info("LBS长度是" + LBSlenStr);

		/**
		 * 解析国家代号
		 */
		
		int MCC= (MCCArr[0] & 0xff) * 256 + (MCCArr[1] & 0xff);

		float c = (float)(MCC*1.0);
		logger.info("国家代号是" + c);

		/**
		 * 解析移动网号码
		 */
		
		
		int MNC = MNCArr[0] & 0xff;
		float n = (float)(MNC*1.0);
		logger.info("移动网号码是" + n);

		/**
		 * 解析位置区码
		 */
		
		int LAC= (LACArr[0] & 0xff) * 256 + (LACArr[1] & 0xff);

		float a = (float)(LAC*1.0);
		logger.info("位置区码是" + a);

		/**
		 * 解析移动基站
		 */
		
		int Cell= (ClDArr[0] & 0xff) * 65536 + (ClDArr[1] & 0xff) * 256 +(ClDArr[2] & 0xff);

		float ce = (float)(Cell*1.0);
		logger.info("移动基站是" + ce);

		/**
		 * 解析终端信息
		 */

		byte termInfo = termInfoArr[0];
		if ((termInfo & 0x80) == 0x80) {
			logger.info("油电断开");
					
		} else {
			logger.info("油电接通");
					
				}
		  if ((termInfo & 0x40) == 0x40) {
			logger.info("Gps已定位");
					
			} else {
				logger.info("GPS未定位");
					
			}
		  if ((termInfo & 0x38) == 0x20) {
			logger.info("SOS求救");
					
			} else if((termInfo & 0x38) == 0x18){
					
				logger.info("低电报警");
			}else if((termInfo & 0x38) == 0x10){
				logger.info("断电报警");
			}else if((termInfo & 0x38) == 0x08){
				logger.info("震动报警");
			}else{
				logger.info("正常");
			}

		  if ((termInfo & 0x04) == 0x04) {
					
			logger.info("已接电源充电");
			} else {
					
				logger.info("未接电源充电");
			}
		  if ((termInfo & 0x02) == 0x02) {
					
			logger.info("ACC高");
			} else {
				logger.info("ACC低");
					
			}
		  if ((termInfo & 0x01) == 0x01) {
			
			logger.info("设防");
			}else{
				logger.info("撤防");
			}

		/**
		 * 解析电压等级
		 */

		byte butter = butterArr[0];
		switch (butter) {
			case 0x00:
				logger.info("无电(关机）");
				break;
			case 0x01:
				logger.info("电量极低(不足以打电话发短信等）");
				break;
			case 0x02:
				logger.info("点亮很低(低电报警）");
				break;
			case 0x03:
				logger.info("电量低(可正常使用）");
				break;
			case 0x04:
				logger.info("电量中");
				break;
			case 0x05:
				logger.info("电量高");
				break;
			case 0x06:
				logger.info("电量极高");
				break;
			default:
				break;
			}

		/**
		 * 解析GSM信号等级
		 */
		byte Gsm = GSMArr[0];
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

		/**
		 * 解析报警内容及语言
		 */

		byte alarmCont1 = alarmContArr[0];
		byte alarmCont2 = alarmContArr[1];
		switch (alarmCont1) {
			case 0x00:
				logger.info("正常");
				break;
			case 0x01:
				logger.info("SOS求救）");
				break;
			case 0x02:
				logger.info("断电报警");
				break;
			case 0x03:
				logger.info("震动报警");
				break;
			case 0x04:
				logger.info("进围栏报警");
				break;
			case 0x05:
				logger.info("出围栏报警");
				break;
			case 0x06:
				logger.info("超速报警");
				break;
		        case 0x09:
				logger.info("位移报警");
				break;
			case 0x0A:
				logger.info("进GPS盲区报警");
				break;
			case 0x0B:
				logger.info("出GPS盲区报警");
				break;
			case 0x0C:
				logger.info("开机报警");
				break;
			case 0x0D:
				logger.info("GPS第一次定位报警");
				break;
			case 0x0E:
				logger.info("外电低电报警");
				break;
			case 0x0F:
				logger.info("外电低电保护报警");
				break;
			case 0x10:
				logger.info("换卡报警");
				break;
			case 0x11:
				logger.info("关机报警");
				break;
			case 0x12:
				logger.info("外电低电保护后飞行模式报警");
				break;
			case 0x13:
				logger.info("拆卸报警");
				break;
			default:
				break;
			}

		switch (alarmCont2) {
			case 0x01:
				logger.info("中文");
				break;
			case 0x02:
				logger.info("英文");
				break;
			case 0x00:
				logger.info("不需要平台回复");
				break;
			default:
				break;
			} 

		/**
		 * 回复信息的构建
		 */

		logger.info("***************定位包回复************************");
			StringBuilder strBuilder = new StringBuilder();
			//添加起始位
			strBuilder.append("7878");
				
			//添加包长度
			strBuilder.append("05");
				
			//添加协议号
			strBuilder.append("26");
				
			//添加信息序列号
			strBuilder.append(msg.getSequence());
			
			//计算出CRC值
			String CRCString = strBuilder.substring(4, strBuilder.length());
			char CRCValue = GenCRC.getCrc16(ConvertTool.hexStringToBytes(CRCString));
			String CRC = Integer.toHexString(CRCValue + 0).toUpperCase();
			//添加错误校验位
			strBuilder.append(CRC);
			//添加停止位
			strBuilder.append("0D0A");
			InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
			logger.error("定位包回复：IP["+address.getAddress()+":"+ address.getPort() +"]: " + strBuilder.toString());
			logger.info("**********************************************");
			//将字符串转化为byte数组
			byte[] response = ConvertTool.hexStringToBytes(strBuilder.toString());
			return response;
			}	
	public byte[] LBS(MessagePackage msg, IoSession session){

		byte[]context = msg.getContext();

		byte[]dateInfoArr = new byte[6];
		byte[]MCCArr = new byte[2];
		byte[]MNCArr = new byte[1];
		byte[]LACArr = new byte[2];
		byte[]ClArr = new byte[3];
		byte[]RSSIArr = new byte[1];
		byte[]NLAC1Arr = new byte[2];
		byte[]NCI1Arr = new byte[3];
		byte[]NRSSI1Arr = new byte[1];
		byte[]NLAC2Arr = new byte[2];
		byte[]NCI2Arr = new byte[3];
		byte[]NRSSI2Arr = new byte[1];
		byte[]NLAC3Arr = new byte[2];
		byte[]NCI3Arr = new byte[3];
		byte[]NRSSI3Arr = new byte[1];
		byte[]NLAC4Arr = new byte[2];
		byte[]NCI4Arr = new byte[3];
		byte[]NRSSI4Arr = new byte[1];
		byte[]NLAC5Arr = new byte[2];
		byte[]NCI5Arr = new byte[3];
		byte[]NRSSI5Arr = new byte[1];
		byte[]NLAC6Arr = new byte[2];
		byte[]NCI6Arr = new byte[3];
		byte[]NRSSI6Arr = new byte[1];
		byte[]DifferArr = new byte[1];
		byte[]LanArr = new byte[2];
		
		int pos = 0;
		System.arraycopy(context,pos,dateInfoArr,0,dateInfoArr.length);
		pos += dateInfoArr.length;
		System.arraycopy(context,pos,MCCArr,0,MCCArr.length);
		pos += MCCArr.length;
		System.arraycopy(context,pos,MNCArr,0,MNCArr.length);
		pos += MNCArr.length;
		System.arraycopy(context,pos,LACArr,0,LACArr.length);
		pos += LACArr.length;
		System.arraycopy(context,pos,ClArr,0,ClArr.length);
		pos += ClArr.length;
		System.arraycopy(context,pos,RSSIArr,0,RSSIArr.length);
		pos += RSSIArr.length;
		System.arraycopy(context,pos,NLAC1Arr,0,NLAC1Arr.length);
		pos += NLAC1Arr.length;
		System.arraycopy(context,pos,NCI1Arr,0,NCI1Arr.length);
		pos += NCI1Arr.length;
		System.arraycopy(context,pos,NRSSI1Arr,0,NRSSI1Arr.length);
		pos += NRSSI1Arr.length;
		System.arraycopy(context,pos,NLAC2Arr,0,NLAC2Arr.length);
		pos += NLAC2Arr.length;
		System.arraycopy(context,pos,NCI2Arr,0,NCI2Arr.length);
		pos += NCI2Arr.length;
		System.arraycopy(context,pos,NRSSI2Arr,0,NRSSI2Arr.length);
		pos += NRSSI2Arr.length;
		System.arraycopy(context,pos,NLAC3Arr,0,NLAC3Arr.length);
		pos += NLAC3Arr.length;
		System.arraycopy(context,pos,NCI3Arr,0,NCI3Arr.length);
		pos += NCI3Arr.length;
		System.arraycopy(context,pos,NRSSI3Arr,0,NRSSI3Arr.length);
		pos += NRSSI3Arr.length;
		System.arraycopy(context,pos,NLAC4Arr,0,NLAC4Arr.length);
		pos += NLAC4Arr.length;
		System.arraycopy(context,pos,NCI4Arr,0,NCI4Arr.length);
		pos += NCI4Arr.length;
		System.arraycopy(context,pos,NRSSI4Arr,0,NRSSI4Arr.length);
		pos += NRSSI4Arr.length;
		System.arraycopy(context,pos,NLAC5Arr,0,NLAC5Arr.length);
		pos += NLAC5Arr.length;
		System.arraycopy(context,pos,NCI5Arr,0,NCI5Arr.length);
		pos += NCI5Arr.length;
		System.arraycopy(context,pos,NRSSI5Arr,0,NRSSI5Arr.length);
		pos += NRSSI5Arr.length;
		System.arraycopy(context,pos,NLAC6Arr,0,NLAC6Arr.length);
		pos += NLAC6Arr.length;
		System.arraycopy(context,pos,NCI6Arr,0,NCI6Arr.length);
		pos += NCI6Arr.length;
		System.arraycopy(context,pos,NRSSI6Arr,0,NRSSI6Arr.length);
		pos += NRSSI6Arr.length;
		System.arraycopy(context,pos,DifferArr,0,DifferArr.length);
		pos += DifferArr.length;
		System.arraycopy(context,pos,LanArr,0,LanArr.length);
		pos += LanArr.length;

		/**
		 * 解析日期时间信息
		 */

		logger.info("***************定位包解析************************");
		
		
		int year = dateInfoArr[0] & 0xff;
		int month = dateInfoArr[1] & 0xff;
		int day = dateInfoArr[2] & 0xff;
		int h = dateInfoArr[3] & 0xff;
		int min = dateInfoArr[4] & 0xff;
		int s = dateInfoArr[5] & 0xff;
		logger.info("时间是："+ year +"年" + month + "月" + day + "日" + h + "时" + min + "分" + s + "秒");


		/**
		 * 解析国家代号
		 */
		
		int MCC= (MCCArr[0] & 0xff) * 256 + (MCCArr[1] & 0xff);

		float c = (float)(MCC*1.0);
		logger.info("国家代号是" + c);

		/**
		 * 解析移动网号码
		 */
		
		int MNC = MNCArr[0] & 0xff;
		float n = (float)(MNC*1.0);
		logger.info("移动网号码是" + n);

		/**
		 * 解析位置区码
		 */
		
		int LAC= (LACArr[0] & 0xff) * 256 + (LACArr[1] & 0xff);

		float a = (float)(LAC*1.0);
		logger.info("位置区码是" + a);

		/**
		 * 解析移动基站
		 */
		
		int CI= (ClArr[0] & 0xff) * 65536 + (ClArr[1] & 0xff) * 256 +(ClArr[2] & 0xff);

		float cc = (float)(CI*1.0);
		logger.info("移动基站是" + cc);

	 	/**
		 * 解析小区信号强度     
		 */

		byte RSSI = RSSIArr[0];
		int rs=RSSI & 0xff;

		logger.info("信号强度是" + rs);
		
		/**
		 * 解析NLAC1
		 */

		int NLAC1= (NLAC1Arr[0] & 0xff) * 256 + (NLAC1Arr[1] & 0xff);

		float na1 = (float)(NLAC1*1.0);
		logger.info("NLAC1是" + na1);
		
		/**
		 * 解析NCI1
		 */
	 	
		int NCI1= (NCI1Arr[0] & 0xff) * 65536 + (NCI1Arr[1] & 0xff) * 256 +(NCI1Arr[2] & 0xff);

		float n1 = (float)(NCI1*1.0);
		logger.info("移动基站是" + n1);

	 	/**
		 * 解析小区信号强度NRSSI1     
		 */

		byte NRSSI1 = NRSSI1Arr[0];
		int rs1=NRSSI1 & 0xff;

		logger.info("信号强度是" + rs1);
		
		
		/**
		 * 解析NLAC2
		 */

		int NLAC2= (NLAC2Arr[0] & 0xff) * 256 + (NLAC2Arr[1] & 0xff);

		float na2 = (float)(NLAC2*1.0);
		logger.info("NLAC2是" + na2);
		
		
		/**
		 * 解析NCI2
		 */
	 	
		int NCI2= (NCI2Arr[0] & 0xff) * 65536 + (NCI2Arr[1] & 0xff) * 256 +(NCI2Arr[2] & 0xff);

		float n2 = (float)(NCI2*1.0);
		logger.info("移动基站是" + n2);

	 	/**
		 * 解析小区信号强度NRSSI2    
		 */

		byte NRSSI2 = NRSSI2Arr[0];
		int rs2=NRSSI2 & 0xff;

		logger.info("信号强度是" + rs2);
		

		/**
		 * 解析NLAC3
		 */

		int NLAC3= (NLAC3Arr[0] & 0xff) * 256 + (NLAC3Arr[1] & 0xff);

		float na3 = (float)(NLAC3*1.0);
		logger.info("NLAC3是" + na3);

		/**
		 * 解析NCI3
		 */
	 	
		int NCI3= (NCI3Arr[0] & 0xff) * 65536 + (NCI3Arr[1] & 0xff) * 256 +(NCI3Arr[2] & 0xff);

		float n3 = (float)(NCI3*1.0);
		logger.info("移动基站是" + n3);

	 	/**
		 * 解析小区信号强度NRSSI3    信号强度怎么表示 ？
		 */

		byte NRSSI3 = NRSSI3Arr[0];
		int rs3=NRSSI3 & 0xff;

		logger.info("信号强度是" + rs3);
		

		/**
		 * 解析NLAC4
		 */

		int NLAC4= (NLAC4Arr[0] & 0xff) * 256 + (NLAC4Arr[1] & 0xff);

		float na4 = (float)(NLAC4*1.0);
		logger.info("NLAC4是" + na4);

		/**
		 * 解析NCI4
		 */
	 	
		int NCI4= (NCI4Arr[0] & 0xff) * 65536 + (NCI4Arr[1] & 0xff) * 256 +(NCI4Arr[2] & 0xff);

		float n4 = (float)(NCI4*1.0);
		logger.info("移动基站是" + n4);

	 	/**
		 * 解析小区信号强度NRSSI4    信号强度怎么表示 ？
		 */

		byte NRSSI4 = NRSSI4Arr[0];
		int rs4=NRSSI4 & 0xff;

		logger.info("信号强度是" + rs4);
		

		/**
		 * 解析NLAC5
		 */

		int NLAC5= (NLAC5Arr[0] & 0xff) * 256 + (NLAC5Arr[1] & 0xff);

		float na5 = (float)(NLAC5*1.0);
		logger.info("NLAC5是" + na5);

		/**
		 * 解析NCI5
		 */
	 	
		int NCI5= (NCI5Arr[0] & 0xff) * 65536 + (NCI5Arr[1] & 0xff) * 256 +(NCI5Arr[2] & 0xff);

		float n5 = (float)(NCI5*1.0);
		logger.info("移动基站是" + n5);

	 	/**
		 * 解析小区信号强度NRSSI5    
		 */

		byte NRSSI5 = NRSSI5Arr[0];
		int rs5=NRSSI5 & 0xff;

		logger.info("信号强度是" + rs5);
		
				
		/**
		 * 解析NLAC6
		 */

		int NLAC6= (NLAC6Arr[0] & 0xff) * 256 + (NLAC6Arr[1] & 0xff);

		float na6 = (float)(NLAC6*1.0);
		logger.info("NLAC6是" + na6);

		/**
		 * 解析NCI6
		 */
	 	
		int NCI6= (NCI6Arr[0] & 0xff) * 65536 + (NCI6Arr[1] & 0xff) * 256 +(NCI6Arr[2] & 0xff);

		float n6 = (float)(NCI6*1.0);
		logger.info("移动基站是" + n6);

		/**
		 * 解析小区信号强度NRSSI6    
		 */

		byte NRSSI6 = NRSSI6Arr[0];
		int rs6=NRSSI6 & 0xff;

		logger.info("信号强度是" + rs6);
		

		/**
		 * 解析时间提前量
		 */

		int differ = DifferArr[0] & 0xff;
		float dif = (float)(differ *1.0);
		logger.info("时间提前量是" + dif);

		
		
		/**
		 * 解析语言信息
		 */

		byte Lan = LanArr[1];
			switch (Lan) {
				case 0x01:
					logger.info("中文");
					break;
				case 0x02:
					logger.info("英文");
					break;
				default:
					break;
				}

		/**
		 * 回复信息的构建
		 */

		logger.info("***************LBS多基站扩展信息包回复************************");
			StringBuilder strBuilder = new StringBuilder();
			//添加起始位
			strBuilder.append("7878");
				
			//添加包长度
			strBuilder.append("05");
				
			//添加协议号
			strBuilder.append("29");
				
			//添加信息序列号
			strBuilder.append(msg.getSequence());
			
			//计算出CRC值
			String CRCString = strBuilder.substring(4, strBuilder.length());
			char CRCValue = GenCRC.getCrc16(ConvertTool.hexStringToBytes(CRCString));
			String CRC = Integer.toHexString(CRCValue + 0).toUpperCase();
			//添加错误校验位
			strBuilder.append(CRC);
			//添加停止位
			strBuilder.append("0D0A");
			InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
			logger.error("定位包回复：IP["+address.getAddress()+":"+ address.getPort() +"]: " + strBuilder.toString());
			logger.info("**********************************************");
			//将字符串转化为byte数组
			byte[] response = ConvertTool.hexStringToBytes(strBuilder.toString());
			return response;
			}	
	
	public byte[] WIFI(MessagePackage msg, IoSession session){

		byte[]context = msg.getContext();

		byte[]dateInfoArr = new byte[6];
		byte[]MCCArr = new byte[2];
		byte[]MNCArr = new byte[1];
		byte[]LACArr = new byte[2];
		byte[]ClArr = new byte[3];
		byte[]RSSIArr = new byte[1];
		byte[]NLAC1Arr = new byte[2];
		byte[]NCI1Arr = new byte[3];
		byte[]NRSSI1Arr = new byte[1];
		byte[]NLAC2Arr = new byte[2];
		byte[]NCI2Arr = new byte[3];
		byte[]NRSSI2Arr = new byte[1];
		byte[]NLAC3Arr = new byte[2];
		byte[]NCI3Arr = new byte[3];
		byte[]NRSSI3Arr = new byte[1];
		byte[]NLAC4Arr = new byte[2];
		byte[]NCI4Arr = new byte[3];
		byte[]NRSSI4Arr = new byte[1];
		byte[]NLAC5Arr = new byte[2];
		byte[]NCI5Arr = new byte[3];
		byte[]NRSSI5Arr = new byte[1];
		byte[]NLAC6Arr = new byte[2];
		byte[]NCI6Arr = new byte[3];
		byte[]NRSSI6Arr = new byte[1];
		byte[]DifferArr = new byte[1];
		byte[]wifiNumArr = new byte[1];
		byte[]wifiMAC1Arr = new byte[6];
		byte[]wifiSS1Arr = new byte[1];
		byte[]wifiMAC2Arr = new byte[6];
		byte[]wifiSS2Arr = new byte[1];
		
		
		int pos = 0;
		System.arraycopy(context,pos,dateInfoArr,0,dateInfoArr.length);
		pos += dateInfoArr.length;
		System.arraycopy(context,pos,MCCArr,0,MCCArr.length);
		pos += MCCArr.length;
		System.arraycopy(context,pos,MNCArr,0,MNCArr.length);
		pos += MNCArr.length;
		System.arraycopy(context,pos,LACArr,0,LACArr.length);
		pos += LACArr.length;
		System.arraycopy(context,pos,ClArr,0,ClArr.length);
		pos += ClArr.length;
		System.arraycopy(context,pos,RSSIArr,0,RSSIArr.length);
		pos += RSSIArr.length;
		System.arraycopy(context,pos,NLAC1Arr,0,NLAC1Arr.length);
		pos += NLAC1Arr.length;
		System.arraycopy(context,pos,NCI1Arr,0,NCI1Arr.length);
		pos += NCI1Arr.length;
		System.arraycopy(context,pos,NRSSI1Arr,0,NRSSI1Arr.length);
		pos += NRSSI1Arr.length;
		System.arraycopy(context,pos,NLAC2Arr,0,NLAC2Arr.length);
		pos += NLAC2Arr.length;
		System.arraycopy(context,pos,NCI2Arr,0,NCI2Arr.length);
		pos += NCI2Arr.length;
		System.arraycopy(context,pos,NRSSI2Arr,0,NRSSI2Arr.length);
		pos += NRSSI2Arr.length;
		System.arraycopy(context,pos,NLAC3Arr,0,NLAC3Arr.length);
		pos += NLAC3Arr.length;
		System.arraycopy(context,pos,NCI3Arr,0,NCI3Arr.length);
		pos += NCI3Arr.length;
		System.arraycopy(context,pos,NRSSI3Arr,0,NRSSI3Arr.length);
		pos += NRSSI3Arr.length;
		System.arraycopy(context,pos,NLAC4Arr,0,NLAC4Arr.length);
		pos += NLAC4Arr.length;
		System.arraycopy(context,pos,NCI4Arr,0,NCI4Arr.length);
		pos += NCI4Arr.length;
		System.arraycopy(context,pos,NRSSI4Arr,0,NRSSI4Arr.length);
		pos += NRSSI4Arr.length;
		System.arraycopy(context,pos,NLAC5Arr,0,NLAC5Arr.length);
		pos += NLAC5Arr.length;
		System.arraycopy(context,pos,NCI5Arr,0,NCI5Arr.length);
		pos += NCI5Arr.length;
		System.arraycopy(context,pos,NRSSI5Arr,0,NRSSI5Arr.length);
		pos += NRSSI5Arr.length;
		System.arraycopy(context,pos,NLAC6Arr,0,NLAC6Arr.length);
		pos += NLAC6Arr.length;
		System.arraycopy(context,pos,NCI6Arr,0,NCI6Arr.length);
		pos += NCI6Arr.length;
		System.arraycopy(context,pos,NRSSI6Arr,0,NRSSI6Arr.length);
		pos += NRSSI6Arr.length;
		System.arraycopy(context,pos,DifferArr,0,DifferArr.length);
		pos += DifferArr.length;
		System.arraycopy(context,pos,wifiNumArr,0,wifiNumArr.length);
		pos += wifiNumArr.length;
		System.arraycopy(context,pos,wifiMAC1Arr,0,wifiMAC1Arr.length);
		pos += wifiMAC1Arr.length;
		System.arraycopy(context,pos,wifiSS1Arr,0,wifiSS1Arr.length);
		pos += wifiSS1Arr.length;
		System.arraycopy(context,pos,wifiMAC2Arr,0,wifiMAC2Arr.length);
		pos += wifiMAC2Arr.length;
		System.arraycopy(context,pos,wifiSS2Arr,0,wifiSS2Arr.length);
		pos += wifiSS2Arr.length;



		/**
		 * 解析日期时间信息
		 */

		logger.info("***************WIFI包解析************************");
		
		
		int year = dateInfoArr[0] & 0xff;
		int month = dateInfoArr[1] & 0xff;
		int day = dateInfoArr[2] & 0xff;
		int h = dateInfoArr[3] & 0xff;
		int min = dateInfoArr[4] & 0xff;
		int s = dateInfoArr[5] & 0xff;
		logger.info("时间是："+ year +"年" + month + "月" + day + "日" + h + "时" + min + "分" + s + "秒");


		/**
		 * 解析国家代号
		 */
		
		int MCC= (MCCArr[0] & 0xff) * 256 + (MCCArr[1] & 0xff);

		float c = (float)(MCC*1.0);
		logger.info("国家代号是" + c);

		/**
		 * 解析移动网号码
		 */
		
		int MNC = MNCArr[0] & 0xff;
		float n = (float)(MNC*1.0);
		logger.info("移动网号码是" + n);

		/**
		 * 解析位置区码
		 */
		
		int LAC= (LACArr[0] & 0xff) * 256 + (LACArr[1] & 0xff);

		float a = (float)(LAC*1.0);
		logger.info("位置区码是" + a);

		/**
		 * 解析移动基站
		 */
		
		int CI= (ClArr[0] & 0xff) * 65536 + (ClArr[1] & 0xff) * 256 +(ClArr[2] & 0xff);

		float cc = (float)(CI*1.0);
		logger.info("移动基站是" + cc);

		/**
		 * 解析小区信号强度    
		 */

		byte RSSI = RSSIArr[0];
		int rs=RSSI & 0xff;

		logger.info("信号强度是" + rs);
		
		/**
		 * 解析NLAC1
		 */

		int NLAC1= (NLAC1Arr[0] & 0xff) * 256 + (NLAC1Arr[1] & 0xff);

		float na1 = (float)(NLAC1*1.0);
		logger.info("NLAC1是" + na1);
		
		/**
		 * 解析NCI1
		 */
	 	
		int NCI1= (NCI1Arr[0] & 0xff) * 65536 + (NCI1Arr[1] & 0xff) * 256 +(NCI1Arr[2] & 0xff);

		float n1 = (float)(NCI1*1.0);
		logger.info("移动基站是" + n1);

	 	/**
		 * 解析小区信号强度NRSSI1    
		 */

		byte NRSSI1 = NRSSI1Arr[0];
		int rs1=NRSSI1 & 0xff;

		logger.info("信号强度是" + rs1);
		
		/**
		 * 解析NLAC2
		 */

		int NLAC2= (NLAC2Arr[0] & 0xff) * 256 + (NLAC2Arr[1] & 0xff);

		float na2 = (float)(NLAC2*1.0);
		logger.info("NLAC2是" + na2);
		
		
		/**
		 * 解析NCI2
		 */
	 	
		int NCI2= (NCI2Arr[0] & 0xff) * 65536 + (NCI2Arr[1] & 0xff) * 256 +(NCI2Arr[2] & 0xff);

		float n2 = (float)(NCI2*1.0);
		logger.info("移动基站是" + n2);

	 	/**
		 * 解析小区信号强度NRSSI2    
		 */

		byte NRSSI2 = NRSSI2Arr[0];
		int rs2=NRSSI2 & 0xff;

		logger.info("信号强度是" + rs2);

		/**
		 * 解析NLAC3
		 */

		int NLAC3= (NLAC3Arr[0] & 0xff) * 256 + (NLAC3Arr[1] & 0xff);

		float na3 = (float)(NLAC3*1.0);
		logger.info("NLAC3是" + na3);

		/**
		 * 解析NCI3
		 */
	 	
		int NCI3= (NCI3Arr[0] & 0xff) * 65536 + (NCI3Arr[1] & 0xff) * 256 +(NCI3Arr[2] & 0xff);

		float n3 = (float)(NCI3*1.0);
		logger.info("移动基站是" + n3);

		/**
		 * 解析小区信号强度NRSSI3    信号强度怎么表示 ？
		 */

		byte NRSSI3 = NRSSI3Arr[0];
		int rs3 = NRSSI3 & 0xff;

		logger.info("信号强度是" + rs3);

		/**
		 * 解析NLAC4
		 */

		int NLAC4= (NLAC4Arr[0] & 0xff) * 256 + (NLAC4Arr[1] & 0xff);

		float na4 = (float)(NLAC4*1.0);
		logger.info("NLAC4是" + na4);

		/**
		 * 解析NCI4
		 */
	 	
		int NCI4= (NCI4Arr[0] & 0xff) * 65536 + (NCI4Arr[1] & 0xff) * 256 +(NCI4Arr[2] & 0xff);

		float n4 = (float)(NCI4*1.0);
		logger.info("移动基站是" + n4);

		/**
		 * 解析小区信号强度NRSSI4    
		 */

		byte NRSSI4 = NRSSI4Arr[0];
		int rs4 = NRSSI4 & 0xff;

		logger.info("信号强度是" + rs4);

		/**
		 * 解析NLAC5
		 */

		int NLAC5= (NLAC5Arr[0] & 0xff) * 256 + (NLAC5Arr[1] & 0xff);

		float na5 = (float)(NLAC5*1.0);
		logger.info("NLAC5是" + na5);

		/**
		 * 解析NCI5
		 */
	 	
		int NCI5= (NCI5Arr[0] & 0xff) * 65536 + (NCI5Arr[1] & 0xff) * 256 +(NCI5Arr[2] & 0xff);

		float n5 = (float)(NCI5*1.0);
		logger.info("移动基站是" + n5);

		/**
		 * 解析小区信号强度NRSSI5    
		 */

		byte NRSSI5 = NRSSI5Arr[0];
		int rs5 = NRSSI5 & 0xff;

		logger.info("信号强度是" + rs5);
		/**
		 * 解析NLAC6
		 */

		int NLAC6= (NLAC6Arr[0] & 0xff) * 256 + (NLAC6Arr[1] & 0xff);

		float na6 = (float)(NLAC6*1.0);
		logger.info("NLAC6是" + na6);

		/**
		 * 解析NCI6
		 */
	 	
		int NCI6= (NCI6Arr[0] & 0xff) * 65536 + (NCI6Arr[1] & 0xff) * 256 +(NCI6Arr[2] & 0xff);

		float n6 = (float)(NCI6*1.0);
		logger.info("移动基站是" + n6);

		/**
		 * 解析小区信号强度NRSSI6    
		 */

		byte NRSSI6 = NRSSI6Arr[0];
		int rs6=NRSSI6 & 0xff;

		logger.info("信号强度是" + rs6);

		/**
		 * 解析时间提前量
		 */

		int differ = DifferArr[0] & 0xff;
		float dif = (float)(differ *1.0);
		logger.info("时间提前量是" + dif);

		
		
		/**
		 * 解析wifi数量
		 */

		byte wifiNum = wifiNumArr[0];
		int wifiN = wifiNum & 0xff;
		logger.info("wifi数量是" + wifiN);
		return null;
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
	
	public byte[] GPS(MessagePackage msg, IoSession session){

		byte[]context = msg.getContext();

		byte[]dateInfoArr = new byte[6];
		byte[]satArr = new byte[1];
		byte[]LatArr = new byte[4];
		byte[]LngArr = new byte[4];
		byte[]VelArr = new byte[1];
		byte[]HeadingArr = new byte[2];
		byte[]MCCArr = new byte[2];
		byte[]MNCArr = new byte[1];
		byte[]LACArr = new byte[2];
		byte[]ClDArr = new byte[3];
		byte[]ACCArr = new byte[1];
		byte[]dataArr = new byte[1];
		byte[]uploadArr = new byte[1];
		byte[]MilArr = new byte[4];
		
		int pos = 0;
		System.arraycopy(context,pos,dateInfoArr,0,dateInfoArr.length);
		pos += dateInfoArr.length;
		System.arraycopy(context,pos,satArr,0,satArr.length);
		pos += satArr.length;
		System.arraycopy(context,pos,LatArr,0,LatArr.length);
		pos += LatArr.length;
		System.arraycopy(context,pos,LngArr,0,LngArr.length);
		pos += LngArr.length;
		System.arraycopy(context,pos,VelArr,0,VelArr.length);
		pos += VelArr.length;
		System.arraycopy(context,pos,HeadingArr,0,HeadingArr.length);
		pos += HeadingArr.length;
		System.arraycopy(context,pos,MCCArr,0,MCCArr.length);
		pos += MCCArr.length;
		System.arraycopy(context,pos,MNCArr,0,MNCArr.length);
		pos += MNCArr.length;
		System.arraycopy(context,pos,LACArr,0,LACArr.length);
		pos += LACArr.length;
		System.arraycopy(context,pos,ClDArr,0,ClDArr.length);
		pos += ClDArr.length;
		System.arraycopy(context,pos,ACCArr,0,ACCArr.length);
		pos += ACCArr.length;
		System.arraycopy(context,pos,dataArr,0,dataArr.length);
		pos += dataArr.length;
		System.arraycopy(context,pos,uploadArr,0,uploadArr.length);
		pos += uploadArr.length;
		System.arraycopy(context,pos,MilArr,0,MilArr.length);
		pos += MilArr.length;

		/**
		 * 解析日期时间信息
		 */

		logger.info("***************定位包解析************************");
		
		
		int year = dateInfoArr[0] & 0xff;
		int month = dateInfoArr[1] & 0xff;
		int day = dateInfoArr[2] & 0xff;
		int h = dateInfoArr[3] & 0xff;
		int min = dateInfoArr[4] & 0xff;
		int s = dateInfoArr[5] & 0xff;
		logger.info("时间是："+ year +"年" + month + "月" + day + "日" + h + "时" + min + "分" + s + "秒");

		
		/**
		 * 解析GPS信息卫星数
		 */

		byte sat = satArr[0]; 
		int GPSlen = sat & 0xf0 ;
		float len = (float) (GPSlen * 1.0 / 16.0);
		int satNum = sat & 0x0f ;
		float num = (float) (satNum * 1.0);
		logger.info("GPS信息长度是" + len + "参与定位卫星数是" + num);

		
		/**
		 * 解析纬度
		 */
		
		int l = (LatArr[0] & 0xff) * 16777216 + (LatArr[1] & 0xff) * 65536+ (LatArr[2] & 0xff) * 256 +(LatArr[3] & 0xff);

		float lat = (float)(l*1.0/1800000.0);
		logger.info("纬度是" + lat);

		
		/**
		 * 解析经度
		 */
		
		int ln = (LngArr[0] & 0xff) * 16777216 + (LngArr[1] & 0xff) * 65536+ (LngArr[2] & 0xff) * 256 +(LngArr[3] & 0xff);

		float lng = (float)(ln*1.0/1800000.0);
		logger.info("经度是" + lng);
		

		/**
		 * 解析速度
		 */

		int v = VelArr[0] & 0xff;
		float vel = (float)(v*1.0);
		logger.info("速度是" + vel);

		
		/**
		 * 解析航向状态
		 */
		
		byte highHeading = HeadingArr[0];
		byte lowHeading = HeadingArr[1];
		
		//将高字节bit6实时、差分定位信息取出
		int RT = (highHeading & 0x08) >> 5;

		//将高字节bit5定位已否信息取出
		int ifPos = (highHeading & 0x08) >> 4;

		//将高字节bit4东西经信息取出
		int Lon = (highHeading & 0x08) >> 3;

		//将高字节bit3南北纬信息取出
		int Ltt = (highHeading & 0x08) >> 2;

		//判断航向
		int Head = (highHeading & 0x03) * 256 + (lowHeading & 0xff);

		if ( ifPos == 1 ){
			logger.info( "GPS已定位");
		} else{
			logger.info("GPS未定位");
		}
		if (RT == 1){
			logger.info("差分GPS");
		} else{
			logger.info("实时GPS");
		}
		if ( Lon == 1){
			logger.info("西经");
		} else{
			logger.info("东经");
		}
		if ( Ltt ==1){
			logger.info("北纬");
		}else{
			logger.info("南纬");
		}
		logger.info("航向"+Head+"°");

		/**
		 * 解析国家代号
		 */
		
		int MCC= (MCCArr[0] & 0xff) * 256 + (MCCArr[1] & 0xff);

		float c = (float)(MCC*1.0);
		logger.info("国家代号是" + c);

		/**
		 * 解析移动网号码
		 */
		
		int MNC = MNCArr[0] & 0xff;
		float n = (float)(MNC*1.0);
		logger.info("移动网号码是" + n);

		/**
		 * 解析位置区码
		 */
		
		int LAC= (LACArr[0] & 0xff) * 256 + (LACArr[1] & 0xff);

		float a = (float)(LAC*1.0);
		logger.info("位置区码是" + a);

		/**
		 * 解析移动基站
		 */
		
		int Cell= (ClDArr[0] & 0xff) * 65536 + (ClDArr[1] & 0xff) * 256 +(ClDArr[2] & 0xff);

		float ce = (float)(Cell*1.0);
		logger.info("移动基站是" + ce);

		/**
		 * 解析ACC
		 */

		byte ACC = ACCArr[0];
		switch (ACC) {
				case 0x00:
					logger.info("ACC低");
					break;
				case 0x01:
					logger.info("ACC高");
					break;
				
				default:
					break;
				}
		
		/**
		 * 解析数据上报模式
		 */

		byte Data = dataArr[0];
		switch (Data) {
				case 0x00:
					logger.info("定时上报");
					break;
				case 0x01:
					logger.info("定距上报");
					break;
				case 0x02:
					logger.info("拐点上报");
					break;
				case 0x03:
					logger.info("ACC状态改变上传");
					break;
				case 0x04:
					logger.info("从运动变为静止状态后，补传最后一个定位点");
					break;
				case 0x05:
					logger.info("网络断开后重连后，上报之前最后一个有效上传点");
					break;
				
				default:
					break;
				}

		/**
		 * 解析GPS实时补传
		 */
	 	
		byte Upload = uploadArr[0];
		switch (Upload) {
				case 0x00:
					logger.info("实时上传");
					break;
				case 0x01:
					logger.info("补传");
					break;
				
				default:
					break;
				}

		/**
		 * 解析里程统计
		 */
	 	
		int Mil = (MilArr[0] & 0xff) * 16777216 + (MilArr[1] & 0xff) * 65536+ (MilArr[2] & 0xff) * 256 +(MilArr[3] & 0xff);

		float m = (float)(Mil*1.0);
		logger.info("里程是" + m);

		
		/**
		 * 回复信息的构建
		 */

		logger.info("***************定位包回复************************");
			StringBuilder strBuilder = new StringBuilder();
			//添加起始位
			strBuilder.append("7878");
				
			//添加包长度
			strBuilder.append("05");
				
			//添加协议号
			strBuilder.append("25");
				
			//添加信息序列号
			strBuilder.append(msg.getSequence());
			
			//计算出CRC值
			String CRCString = strBuilder.substring(4, strBuilder.length());
			char CRCValue = GenCRC.getCrc16(ConvertTool.hexStringToBytes(CRCString));
			String CRC = Integer.toHexString(CRCValue + 0).toUpperCase();
			//添加错误校验位
			strBuilder.append(CRC);
			//添加停止位
			strBuilder.append("0D0A");
			InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
			logger.error("定位包回复：IP["+address.getAddress()+":"+ address.getPort() +"]: " + strBuilder.toString());
			logger.info("**********************************************");
			//将字符串转化为byte数组
			byte[] response = ConvertTool.hexStringToBytes(strBuilder.toString());
			return response;
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
		long res = Long.parseLong(charToCRC(msg.getSequence()), 16);
		return res;
	}
	
}
