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

#define PORT  5000
#define HOST  "192.168.0.67"
#define BUFFER_SIZE 6

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
    for(int i = 0; i < zer + 1; i++){
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
    ans.push_back(ans.back());
    ans.back().back() = 0;
    int N = ans.size();
    for(int i = 0; i < N; i++){
        for(int j = i; j < N; j++){
            ans[j][i] = ans[i][j]; 
        }
    }
    return retval+1;
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
    std::vector<int> cli_ips;
    std::vector<int> cli_ports;

    while(1){
        fd_set conne;
        FD_ZERO(&conne);
        FD_SET(Servfd, &conne);
        int maxfd = Servfd;

        for(auto it: cli_socks){
            FD_SET(it, &conne);
            if(it > maxfd){
                maxfd = it;
            }
        }

        int activity = select(maxfd+1, &conne, nullptr, nullptr, nullptr);

        if(FD_ISSET(Servfd, &conne)){
            sockaddr_in cli;
            socklen_t clilen = sizeof(cli);
            int new_socket = accept(Servfd, (struct sockaddr*)&cli, &clilen);

            if(new_socket >= 0){
                cli_socks.push_back(new_socket);
                char buffer[BUFFER_SIZE];
                int bytes_read = read(new_socket, (void *)&buffer, BUFFER_SIZE-1);
                cli_ips.push_back(buffer[0] << 24 + buffer[1] << 16 + buffer[2] << 8 + buffer[3]);
                cli_ports.push_back(buffer[4] << 8 + buffer[5]);
                std::cout << "accepted: " << (int)buffer[0] << "." << (int)buffer[1] << "." << (int)buffer[2] << "." << (int)buffer[3] << "." << ":" << cli_ports.back() << " sockfd:" << new_socket;
                if(cli_socks.size() == N){
                    break;
                }
            }
        }
    }

    for(int i = 0; i < cli_socks.size(); i++){
        int numNib = 0;
        for(auto it : adjMat[i]){
            if(it >= 0){
                numNib++;
            }
        }
        char c = 0;
        c += numNib;
        send(cli_socks[i], &c, 1, 0);
        for(int j = 0; i < adjMat[i].size(); j++){
            if(adjMat[i][j] >= 0){
                char c = 'A';
                c += j;
                send(cli_socks[i], &c, 1, 0);
                send(cli_socks[i], &cli_ips[j], 4, 0);
                send(cli_socks[i], &cli_ports[j], 4, 0);
            }
        }
    }

    while(1){
        std::vector<std::vector<int>> newmat;
        make_mat_from_conf(conf, newmat);
        bool change = 0;
        for(int i = 0; i < N; i++){
            for(int j = 0; j < N; j++){
                if(newmat[i][j] != adjMat[i][j]){
                    change = 1;
                    adjMat[i][j] = newmat[i][j];
                }
            }
        }

        if(change){
            for(int i = 0; i < cli_socks.size(); i++){
                int numNib = 0;
                for(auto it : adjMat[i]){
                    if(it >= 0){
                        numNib++;
                    }
                }
                char c = 0;
                c += numNib;
                send(cli_socks[i], &c, 1, 0);
                for(int j = 0; i < adjMat[i].size(); j++){
                    if(adjMat[i][j] >= 0){
                        char c = 'A';
                        c += j;
                        send(cli_socks[i], &c, 1, 0);
                        send(cli_socks[i], &cli_ips[j], 4, 0);
                        send(cli_socks[i], &cli_ports[j], 4, 0);
                    }
                }
            }
        }
    }
}









            // for(auto it = cli_socks.begin(); it != cli_socks.end();){
            //     auto fd = *it;
            //     if(FD_ISSET(fd, &conne)){
            //         char buffer[BUFFER_SIZE];
            //         int bytes_read = read(fd, (void *)&buffer, BUFFER_SIZE-1);
            //         if(bytes_read < 0){
            //             close(fd);
            //             cli_socks.erase(it);
            //         }
            //         else{
            //             buffer[bytes_read] = '\0';
            //             for(int i = 0; i < bytes_read + 1; i++){
            //                 std::cout << buffer[i];
            //             }
            //             it++;
            //         }
            //     }
            //     else{
            //         it++;
            //     }
            // }