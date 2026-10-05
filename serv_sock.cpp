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
#define PORT 5000


int main(){
    char c;
    int sockfd;
    sockfd = socket(PF_INET, SOCK_DGRAM, 0);
    if(sockfd == -1) return -1;

    std::string s = "hello\n";
    int strlen = 6;
    struct sockaddr_in my_addr;
    my_addr.sin_family=AF_INET;
    my_addr.sin_port = htons(PORT);
    my_addr.sin_addr.s_addr = inet_addr(HOST);
    memset(&(my_addr.sin_zero),'\0',sizeof(my_addr.sin_zero));

    struct sockaddr_in cli_addr;
    cli_addr.sin_family=AF_INET;
    cli_addr.sin_port = htons(PORT+9);
    cli_addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    memset(&(cli_addr.sin_zero),'\0',sizeof(cli_addr.sin_zero));
    
    bind(sockfd, (sockaddr *)&my_addr, sizeof(sockaddr));

    for(int i = 0; i < strlen; i++){
        c = s[i];
        sendto(sockfd, (void *)&c, sizeof(c), 0, (sockaddr *)&cli_addr, sizeof(sockaddr));
    }
}
