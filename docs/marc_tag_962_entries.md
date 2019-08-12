# MARC tag 962 subfield $u matching entries.md

This is an old note of some of the URL patterns I came across in MARC tag 962 subfield $u, and the regexes I used to match them.

```python
# http://wellcomeimages.org/ixbin/imageserv?MIRO=L0046161
# http://wellcomeimages.org/ixbin/imageserv?MIRO=V0033167F1
ixbin_re = re.compile(
r'^http://wellcomeimages\.org/ixbin/imageserv\?MIRO=[A-Z][0-9]{7}[A-Z]{0,2}[0-9]?$'
)

# http://wellcomeimages.org/indexplus/image/L0046161.html
# http://wellcomeimages.org/indexplus/image/L0041574.html.html
# http://wellcomeimages.org/indexplus/image/V0032544ECL.html
ixplus_re = re.compile(
r'^http://wellcomeimages\.org/indexplus/image/[A-Z][0-9]{7}[A-Z]{0,3}[0-9]?(?:\.html){0,2}?$'
)

# http://wellcomeimages.org/ixbin/hixclient?MIROPAC=L0076330
# http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V0000492EB
# http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V0031553F1
ixbin_hixclient = re.compile(
r'^http://wellcomeimages.org/ixbin/hixclient\?MIROPAC=[A-Z][0-9]{7}[A-Z]{0,2}[0-9]?$'
)

# http://wellcomeimages.org/ixbinixclient.exe?MIROPAC=V0010851.html.html
ixbinix_client = re.compile(
r'^http://wellcomeimages\.org/ixbinixclient\.exe\?MIROPAC=[A-Z][0-9]{7}\.html\.html$'
)

# http://wellcomeimages.org/ixbinixclient.exe?image=M0009946.html
ixbinix_img_client = re.compile(
r'^http://wellcomeimages\.org/ixbinixclient\.exe\?image=[A-Z][0-9]{7}\.html$'
)

for sierra_id, var_fields in d.items():
    for vf in var_fields:

        if sierra_id == '1608170':  # ???
            continue

        # 1102420 u => http://film.wellcome.ac.uk:15151/mediaplayer.html?fug_7340-1&pw=524ph=600.html
        # 1160105 u => http://www.wellcome.ac.uk/About-us/Contact-us/Visitors/Public-access/index.htm
        # 1175731 u => https://www.flickr.com/photos/65723207@N00/43111530435/in/dateposted-public/
        # 1177872 u => http://ccsa.admin.ch/cgi-bin/hi-res/hi-res.cgi?image=GEVBGE_Da562.jpg&return_url=http%3a%2f%
        # 1187716 u => http://farm3.static.flickr.com/2102/2216691175_361c3c2d76_s.jpg
        # 1203676 u => http://www.wellcomecollection.org/exhibitionsandevents/exhibitions/medicineman/index.htm
        # ('1465561', 'http://www.flickr.com/photos/65723207@N00/6522183545/')
        if 'u' not in vf:
            print(sierra_id, vf)
        if vf['u'].startswith((
            'http://film.wellcome.ac.uk',
            'http://www.wellcome.ac.uk/',
            'https://www.flickr.com/',
            'http://www.flickr.com/',
            'http://ccsa.admin.ch/',
            'http://farm3.static.flickr.com/',
            'http://www.grad.ucl.ac.uk',
            'http://www.museumoflondon.org.uk',
            'http://farm4.static.flickr.com/',
            'http://www.bfi.org.uk',
            'http://farm8.staticflickr.com',
            'http://www.speakingforourselves.org.uk',
        )) or '/exhibitionsandevents/' in vf['u'] or 'staticflickr.com' in vf['u']:
            continue
    # assert ixbin_re.match(vf\['e']), (sierra_id, vf)
        assert (
            ixplus_re.match(vf['u']) or
            ixbin_hixclient.match(vf['u']) or
            ixbinix_client.match(vf['u']) or
            ixbinix_img_client.match(vf['u'])
        ), (sierra_id, vf['u'])
```
