# 文件服务

#### 介绍

基于netty、springboot等开源框架实现文件上传下载服务

目前实行功能:

文件上传实时进度条

文件分段上传

文件断网断点续传

#### 软件架构

springboot

netty4

redis

fastdfs


#### 安装教程

1.  修改redis,fastdfs配置
2.  启动Application
3.  访问http://localhost:7000

#### 使用说明

最好是修改为文件MD5作为KEY值,避免冲突
# fileServer
