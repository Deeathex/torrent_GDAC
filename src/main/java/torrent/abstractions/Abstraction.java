package torrent.abstractions;

import torrent.Torr2;

public interface Abstraction {
    Torr2.Message handle(Torr2.Message requestMessage);
}
