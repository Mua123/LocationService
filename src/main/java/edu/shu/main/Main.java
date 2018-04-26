package edu.shu.main;

import java.io.File;

import org.apache.log4j.PropertyConfigurator;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
	public static void main(String[] args) {
		//加载配置文件，实质是加载spring容器，并往容器中加入所需要的bean
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
		//监听已经建立
		System.out.println("服务器启动成功");
		
		//使用log4j创建日志
		try {
			String path = Main.class.getClassLoader().getResource("").getPath();
			File file = new File(path);
			PropertyConfigurator.configure(file.getAbsolutePath()+"\\log4j.properties");
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			System.out.println("找不到配置文件 请确认“log4j.properties”的位置");
		}
		
		
	}
}
