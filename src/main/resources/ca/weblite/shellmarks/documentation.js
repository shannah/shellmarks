var menuImageURI = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' height='24px' viewBox='0 0 24 24' width='24px' fill='%23000000'%3E%3Cpath d='M0 0h24v24H0z' fill='none'/%3E%3Cpath d='M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z'/%3E%3C/svg%3E";
var moreVertURI = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' height='24px' viewBox='0 0 24 24' width='24px' fill='%23000000'%3E%3Cpath d='M0 0h24v24H0z' fill='none'/%3E%3Cpath d='M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z'/%3E%3C/svg%3E";
var editURI = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' height='24px' viewBox='0 0 24 24' width='24px' fill='%23000000'%3E%3Cpath d='M0 0h24v24H0z' fill='none'/%3E%3Cpath d='M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z'/%3E%3C/svg%3E";
function getSectionName(section) {
    var out = null;
    section.childNodes.forEach(function(node) {
        if (out) return;
        if (node.tagName && node.tagName.toLowerCase().match(/^h\d$/)) {
            out = node.getAttribute('id');
        }
    });
    return out;
}

function injectSectionMenu(section) {
    if (getSectionName(section).startsWith("_")) return;
    var menuButton = document.createElement('a');
    var img = new Image();
    img.src = moreVertURI;
    menuButton.appendChild(img);
    menuButton.classList.add("section-menu");
    //menuButton.setAttribute('href', 'sectionMenu:'+getSectionName(section));
    //menuButton.setAttribute('href', 'sectionMenu:'+section)

    section.insertBefore(menuButton, section.childNodes[0]);

    var menu = document.createElement('div');
    menu.classList.add('section-menu-content');

    var editButton = document.createElement('a');
    editButton.setAttribute('href', 'editSection:'+getSectionName(section));
    editButton.classList.add('section-menu-item');

    var editButtonImage = new Image();
    editButtonImage.src = editURI;

    editButton.appendChild(editButtonImage);
    var editText = document.createElement('span');
    editText.appendChild(document.createTextNode('Edit Section'));
    editButton.appendChild(editText);

    menu.appendChild(editButton);

    menuButton.addEventListener('click', function() {
        if (menu.classList.contains('active')) {
            menu.classList.remove('active');
        } else {
            menu.classList.add('active');
        }
    });

    section.insertBefore(menu, section.childNodes[1]);

}

function injectSectionMenus() {
    for (var i=0; i<8; i++) {
        document.querySelectorAll('div.sect'+i).forEach(injectSectionMenu);
    }
}

injectSectionMenus();