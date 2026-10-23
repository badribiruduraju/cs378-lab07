for i in 1 2 3; do sudo ip netns del n$i; done
sudo ip link del br0