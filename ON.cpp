#include <cstdint>
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
#include <thread>
#include <chrono>
#include <signal.h>

#define PORT  5000
// #define HOST  "127.0.0.1"
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
    // ans.pop_back();
    return 1;
}

int make_mat_from_conf(std::ifstream& fil, std::vector<std::vector<int>>& ans){
    ans.clear();
    int i = 0;
    int retval = 0;
    fil.clear();
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
    for(int i = 0; i < ans.size(); i++){
        for(int j = i; j < ans[i].size(); j++){
            ans[j][i] = ans[i][j]; 
        }
    }
    // for(int i = 0; i < ans.size(); i++){
    //     for(int j = 0; j < ans[i].size(); j++){
    //         std::cout << ans[i][j] << " ";
    //     }
    //     std::cout << "\n";
    // }
    // int N = ans.size();
    // std::cout << "came here" << std::endl;
    return retval;
}


int main(int argc, char* argv[]){
    signal(SIGPIPE, SIG_IGN);
    if(argc <= 2){
        std::cout << "provide config and ip please :3" << std::endl;
        return -1;
    }
    if(argc > 3){
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
    my_addr.sin_addr.s_addr = inet_addr(argv[2]);
    my_addr.sin_family = AF_INET;
    memset(&(my_addr.sin_zero),'\0',sizeof(my_addr.sin_zero));

    bind(Servfd, (sockaddr *)&my_addr, sizeof(sockaddr));
    listen(Servfd,5);
    std::vector<int> cli_socks;
    std::vector<uint32_t> cli_ips;
    std::vector<uint16_t> cli_ports;

    while(1){
        fd_set conne;
        FD_ZERO(&conne);
        FD_SET(Servfd, &conne);
        int maxfd = Servfd;

        // for(auto it: cli_socks){
        //     FD_SET(it, &conne);
        //     if(it > maxfd){
        //         maxfd = it;
        //     }
        // }

        int activity = select(maxfd+1, &conne, nullptr, nullptr, nullptr);

        if(FD_ISSET(Servfd, &conne)){
            sockaddr_in cli;
            socklen_t clilen = sizeof(cli);
            int new_socket = accept(Servfd, (struct sockaddr*)&cli, &clilen);

            if(new_socket >= 0){
                cli_socks.push_back(new_socket);
                unsigned char buffer[BUFFER_SIZE];
                int bytes_read = read(new_socket, (void *)&buffer, BUFFER_SIZE);
                cli_ips.push_back((buffer[0] << 24) + (buffer[1] << 16) + (buffer[2] << 8) + buffer[3]);
                cli_ports.push_back((buffer[4] << 8) + buffer[5]);
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
        for(int j = 0; j < adjMat[i].size(); j++){
            if(adjMat[i][j] >= 0){
                char c = 'A';
                c += j;
                uint32_t net_ip = htonl(cli_ips[j]);
                uint32_t net_pot = htonl(cli_ports[j]);
                uint32_t net_cost = htonl(adjMat[i][j]);
                char tuple_buf[13];
                tuple_buf[0] = c;
                memcpy(tuple_buf + 1, &net_ip, 4);
                memcpy(tuple_buf + 5, &net_pot, 4);
                memcpy(tuple_buf + 9, &net_cost, 4);
                send(cli_socks[i], tuple_buf, 13, 0);
            }
        }
    }

    while(1){
        std::this_thread::sleep_for(std::chrono::seconds(1));
        std::vector<std::vector<int>> newmat;
        make_mat_from_conf(conf, newmat);
        bool change = 0;
        for(int i = 0; i < newmat.size(); i++){
            for(int j = 0; j < newmat[i].size(); j++){
                if(newmat[i][j] != adjMat[i][j]){
                    change = 1;
                    adjMat[i][j] = newmat[i][j];
                }
            }
        }

        if(change){
            if(newmat.size() < N) continue;
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
                for(int j = 0; j < adjMat[i].size(); j++){
                    if(adjMat[i][j] >= 0){
                        char c = 'A';
                        c += j;
                        uint32_t net_ip = htonl(cli_ips[j]);
                        uint32_t net_pot = htonl(cli_ports[j]);
                        uint32_t net_cost = htonl(adjMat[i][j]);
                        char tuple_buf[13];
                        tuple_buf[0] = c;
                        memcpy(tuple_buf + 1, &net_ip, 4);
                        memcpy(tuple_buf + 5, &net_pot, 4);
                        memcpy(tuple_buf + 9, &net_cost, 4);
                        send(cli_socks[i], tuple_buf, 13, 0);
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