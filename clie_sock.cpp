#include <iostream>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>
#include <netinet/in.h>
#include <fstream>
#include <filesystem>

#define HOST "127.0.0.1"
#define PORT 5009


int main(){
    char c;
    int sockfd;
    sockfd = socket(PF_INET, SOCK_DGRAM, 0);
    if(sockfd == -1) return -1;

    std::string s = "";
    struct sockaddr_in my_addr;
     my_addr.sin_family=AF_INET;
    my_addr.sin_port = htons(PORT);
    my_addr.sin_addr.s_addr = inet_addr(HOST);
    memset(&(my_addr.sin_zero),'\0',sizeof(my_addr.sin_zero));

    struct sockaddr_in ser_addr;
    ser_addr.sin_family=AF_INET;
    ser_addr.sin_port = htons(PORT-9);
    ser_addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    memset(&(ser_addr.sin_zero),'\0',sizeof(ser_addr.sin_zero));
    
    bind(sockfd, (sockaddr *)&my_addr, sizeof(sockaddr));


    while(1){
        socklen_t lenfrom = sizeof(struct sockaddr);
        recvfrom(sockfd, (void *)&c, sizeof(c), 0, (sockaddr *)&ser_addr, (socklen_t *)&lenfrom);
        std::cout << c << std::flush;
    }
}
