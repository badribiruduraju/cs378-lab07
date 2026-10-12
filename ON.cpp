#include <iostream>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>
#include <netinet/in.h>
#include <fstream>
#include <filesystem>
#include <vector>

#define PORT  42069
#define HOST  "192.168.0.1"

int numNws(std::string& s){
    int ans = 0;
    for(int i = 0; i < s.length(); i++){
        if(!std::isspace(s[i])){
            ans++;
        }
    }
    return ans;
}

int pushRow(std::string& s, std::vector<int>& ans, int zer){
    bool lws = 1;
    // std::cout << "zer" << "\n";
    for(int i = 0; i < zer; i++){
        ans.push_back(0);
    }
    for(int i = 0; i < s.length(); i++){
        if(std::isspace(s[i])){
            lws = 1;
        }
        else{
            if(lws == 1){
                ans.push_back(s[i] - '0');
            }
            else{
                ans.back() = ans.back()*10 + s[i] - '0';
            }
            lws = 0;
        }
    }
    return 1;
}

int make_mat_from_conf(std::ifstream& fil, std::vector<std::vector<int>>& ans){
    ans.clear();
    int i = 0;
    int retval = 0;
    fil.seekg(0,std::ios::beg);
    std::string line;
    while(std::getline(fil, line)){
        if(line[0] == '#'){
            continue;
        }
        if(numNws(line) == 0){
            continue;
        }
        else{
            ans.push_back({});
            pushRow(line, ans[i], i);
            if(i == 0){
                retval = ans[0].size();
            }
            i++;
        }
    }
    return retval;
}


int main(int argc, char* argv[]){
    if(argc <= 1){
        std::cout << "provide config please :3" << std::endl;
        return -1;
    }
    if(argc > 2){
        std::cout << "what...?" << std::endl;
        return -1;
    }


    std::ifstream conf(argv[1]);
    std::vector<std::vector<int>> adjMat;
    int N = make_mat_from_conf(conf, adjMat);
    for(int i = 0; i < N; i++){
        for(int j = 0; j < N; j++){
            std::cout << adjMat[i][j] << " ";
        }
        std::cout << "\n";
    }

    int Servfd;
    Servfd = socket(PF_INET, SOCK_STREAM, 0);
    if(Servfd == -1) return -1;


    struct sockaddr_in my_addr;
    my_addr.sin_port = htons(PORT);
    my_addr.sin_addr.s_addr = inet_addr(HOST);
    my_addr.sin_family = AF_INET;
    memset(&(my_addr.sin_zero),'\0',sizeof(my_addr.sin_zero));

    bind(Servfd, (sockaddr *)&my_addr, sizeof(sockaddr));
    listen(Servfd,5);
    std::vector<int> cli_socks;


}