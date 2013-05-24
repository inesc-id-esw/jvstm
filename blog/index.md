---
layout: blog
---


{% for post in site.posts %}
  {% assign page = post %}
  {% assign content = post.content %}
  {% include only-the-post.html %}
  
<br/>
-----
<br/>
<br/>
{% endfor %}
