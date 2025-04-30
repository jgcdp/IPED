package iped.parsers.instagram;

import java.util.List;

import dpf.ap.gpinf.interfacetelegram.PhotoData;

public class Contact {
	private String id = null;
	private int groupId;
	private String name = null;
	private String fullname = null;
	private String username = null;
	private String phone = null;
	private byte[] avatar = null;
	private List<PhotoData> photos = null;
	private boolean isGroup;
	private boolean isChannel;

	public Contact(String id) {
		this.id = id;
	}

    public Contact(String id, String username, String fullName) {
        this.id = id;
        this.username = username;
        this.fullname = fullName;
    }

    public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		if (isGroup) {
			return "Group - " + name;
		}
		if (isChannel) {
			return "Channel - " + name;
		}
		return name;
	}

    public String getFullname() {
        if(fullname == null)
            return username;

        return fullname;
    }

    public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String telefone) {
		this.phone = telefone;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public byte[] getAvatar() {
		return avatar;
	}

	public void setAvatar(byte[] avatar) {
		this.avatar = avatar;
	}

	public void setFullname(String fullname) {
		this.fullname = fullname;
	}

	public List<PhotoData> getPhotos() {
		return photos;
	}

	public void setPhotos(List<PhotoData> photos) {
		this.photos = photos;
	}

	public int getGroupId() {
		return groupId;
	}

	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}

	public boolean isGroup() {
		return isGroup;
	}

	public void setGroup(boolean isGroup) {
		this.isGroup = isGroup;
	}

	public boolean isChannel() {
		return isChannel;
	}

	public void setChannel(boolean isChannel) {
		this.isChannel = isChannel;
	}

	public boolean isGroupOrChannel() {
		return isGroup || isChannel;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String name = this.fullname;
		if (name != null) {
			sb.append(name.trim());
		}
		String number = getPhone();
		if (number != null && number.length() > 0) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append("(phone: ").append(number).append(')');
		} else if (getId() != null) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append("(ID:").append(getId()).append(')');
		}
		if (sb.length() == 0) {
			sb.append("[unknown]");
		}
		return sb.toString();
	}
}
