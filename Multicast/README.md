# Multicast-Find-Copies-App

Application that discovers copies of itself in the local network by exchanging multicast UDP messages.
The application should track the moments of appearance and disappearance of other copies of itself in the local network and output the list of IP addresses of "live" copies in case of changes.

Multicast group address should be passed as a parameter to the application.
The application should support work in both IPv4 and IPv6 networks, selecting the protocol automatically depending on the passed group address.
