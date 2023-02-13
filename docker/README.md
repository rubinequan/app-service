# 野火应用服务docker使用说明

## 编译镜像
首先需要先编译应用服务，使用下面命令编译
```
mvn clean package
```

然后进入到docker目录编译镜像
```
sudo docker build -t app-server -f Dockerfile ..
```

## 运行
直接运行：
```
sudo docker run -it -p 8888:8888 -e JVM_XMX=256M -e JVM_XMS=256M app-server
```

配置：
如果配置需要修改，可以修改config目录下的配置，然后重新打包镜像，也可以手动指定配置目录，这样不用重新打包镜像。手动指定配置目录的方法如下，注意路径需要绝对路径
```
sudo docker run -it -v $PATH_TO_CONFIG:/opt/app-server/config -v $PATH_TO_H2DB:/opt/app-server/h2db -e JVM_XMX=256M -e JVM_XMS=256M -p 8888:8888 app-server
```
